(ns braid.ui.views.autocomplete
  (:require [clj-fuzzy.metrics :as fuzzy]
            [chat.client.store :as store]
            [chat.client.schema :as schema]
            [clojure.string :as string]
            [chat.client.views.helpers :refer [id->color debounce]]
            [chat.client.dispatcher :refer [dispatch!]]
            [chat.client.emoji :as emoji])
  (:import [goog.events KeyCodes]))

; fn that returns results that will be shown if pattern matches
;    inputs:
;       text - current text of user's message
;       thread-id - id of the thread
;    output:
;       if no pattern matched, return nil
;       if a trigger pattern was matched, an array of maps, each containing:
;         :html - fn that returns html to be displayed for the result
;             inputs:
;                 none
;             output:
;                 html (as returned by (dom/*) functions)
;         :action - fn to be triggered when result picked
;             inputs:
;             output:
;                 none expected
;         :message-transform - fn to apply to text of message
;             inputs:
;                text
;             output:
;                text to replace message with

(defn- normalize [s]
  (-> (.toLowerCase s)
     (string/replace #"\s" "")))

(defn- simple-matches?
  [m s]
  (not= -1 (.indexOf m s)))

(defn- fuzzy-matches? [m s]
  (let [m (normalize m)
        s (normalize s)]
    (or (simple-matches? m s)
        (< (fuzzy/levenshtein m s) 2))))

(defn emoji-view
  [emoji]
  [:div.emoji-match
    (emoji/shortcode->html (string/replace emoji #"[\(\)]" ":"))
    [:div.name emoji]
    [:div.extra "..."]])

(def engines
  [
   ; ... :emoji  -> autocomplete emoji
   (fn [text _]
     (let [pattern #"\B[:(](\S{2,})$"]
       (when-let [query (second (re-find pattern text))]
         (->> emoji/unicode
              (filter (fn [[k v]]
                        (simple-matches? k query)))
              (map (fn [[k v]]
                     {:key
                      (fn [] k)
                      :action
                      (fn [])
                      :message-transform
                      (fn [text]
                        (string/replace text pattern (str k " ")))
                      :html
                      (fn []
                        [emoji-view (let [show-brackets? (= "(" (first text))
                                          emoji-name (apply str (-> k rest butlast))]
                                      (if show-brackets?
                                         (str "(" emoji-name ")") k))
                                  {:react-key k}])}))))))

   ; ... @<user>  -> autocompletes user name
   (fn [text _]
     (let [pattern #"\B@(\S{0,})$"]
       (when-let [query (second (re-find pattern text))]
         (->> (store/users-in-open-group)
              (filter (fn [u]
                        (fuzzy-matches? (u :nickname) query)))
              (map (fn [user]
                     {:key
                      (fn [] (user :id))
                      :action
                      (fn [])
                      :message-transform
                      (fn [text]
                        (string/replace text pattern (str "@" (user :nickname) " ")))
                      :html
                      (fn []
                        [:div.user-match
                          [:img.avatar {:src (user :avatar)}]
                          [:div.name (user :nickname)]
                          [:div.extra "..."]])}))))))

   ; ... #<tag>   -> autocompletes tag
   (fn [text _]
     (let [pattern #"\B#(\S{0,})$"]
       (when-let [query (second (re-find pattern text))]
         (let [group-tags (store/tags-in-open-group)
               exact-match? (some #(= query (:name %)) group-tags)]
           (->> group-tags
                (filter (fn [t]
                          (fuzzy-matches? (t :name) query)))
                (map (fn [tag]
                       {:key
                        (fn [] (tag :id))
                        :action
                        (fn [])
                        :message-transform
                        (fn [text]
                          (string/replace text pattern (str "#" (tag :name) " ")))
                        :html
                        (fn []
                          [:div.tag-match
                           [:div.color-block{:style {:backgroundColor (id->color (tag :id))}}]
                           [:div.name (tag :name)]
                           [:div.extra (tag :description)]])}))
                (cons (when-not (or exact-match? (string/blank? query))
                        (let [tag (schema/make-tag {:name query
                                                    :group-id (store/open-group-id)
                                                    :group-name (store/open-group-name)})]
                          {:key (constantly (tag :id))
                           :action
                           (fn []
                             (dispatch! :create-tag [(tag :name) (tag :group-id) (tag :id)]))
                           :message-transform
                           (fn [text]
                             (string/replace text pattern (str "#" (tag :name) " ")))
                           :html
                           (fn []
                             [:div.tag-match
                              [:div.color-block{:style {:backgroundColor (id->color (tag :id))}}]
                              [:div.name (str "Create tag " (tag :name))]
                              [:div.extra (:name (store/id->group (tag :group-id)))]])})))
                (remove nil?)
                reverse)))))
   ])
