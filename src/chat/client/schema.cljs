(ns chat.client.schema
  (:require [cljs-uuid-utils.core :as uuid]))


(defn make-message [data]
  {:id (or (data :id) (uuid/make-random-squuid))
   :content (data :content)
   :thread-id (or (data :thread-id) (uuid/make-random-squuid))
   :group-id (data :group-id)
   :user-id (data :user-id)
   :created-at (or (data :created-at) (js/Date.))
   :mentioned-user-ids (or (data :mentioned-user-ids) [])
   :mentioned-tag-ids (or (data :mentioned-tag-ids) [])})

(defn make-tag [data]
  {:id (or (data :id) (uuid/make-random-squuid))
   :name (data :name)
   :group-id (data :group-id)
   :group-name (get data :group-name "")
   :description nil
   :threads-count 0
   :subscribers-count 0})

(defn make-group [data]
  {:id (or (data :id) (uuid/make-random-squuid))
   :name (data :name)
   :admins #{}
   :intro nil
   :avatar nil
   :public? false})

(defn make-invitation [data]
  {:id (or (data :id) (uuid/make-random-squuid))
   :invitee-email (data :invitee-email)
   :group-id (data :group-id)})
