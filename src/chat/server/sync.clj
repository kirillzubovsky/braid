(ns chat.server.sync
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [compojure.core :refer [GET POST routes defroutes context]]
            [taoensso.timbre :as timbre :refer [debugf]]
            [clojure.core.async :as async :refer [<! <!! >! >!! put! chan go go-loop]]
            [chat.server.db :as db]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn (fn [ob]
                                                                          (get-in ob [:session :user-id]))})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(defroutes sync-routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req)))

(defmulti event-msg-handler :id)

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (debugf "Event: %s" event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :chat/new-message
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (doseq [uid (->> (:any @connected-uids)
                   (remove (partial = id)))]
    (db/with-conn (db/create-message! ?data))
    (chsk-send! uid [:chat/new-message ?data])))

(defmethod event-msg-handler :chat/hide-thread
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (db/with-conn (db/user-hide-thread! (get-in ring-req [:session :user-id]) ?data)))

(defmethod event-msg-handler :session/start
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (when-let [user-id (get-in ring-req [:session :user-id])]
    (chsk-send! user-id [:session/init-data (db/with-conn
                                              {:user-id user-id
                                               :users (db/fetch-users)
                                               :messages (db/fetch-messages)
                                               :open-thread-ids (db/get-open-threads-for-user user-id)})])))

(defonce router_ (atom nil))

(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))