(ns braid.ui.views.pages.me
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.string :as string]
            [braid.desktop.notify :as notify]
            [braid.ui.views.pills :refer [tag-pill-view]]
            [chat.client.reagent-adapter :refer [subscribe]]
            [chat.client.dispatcher :refer [dispatch!]]
            [chat.client.store :as store]
            [chat.shared.util :refer [valid-nickname?]])
  (:import [goog.events KeyCodes]))

(defn nickname-view
  []
  (let [format-error (r/atom false)
        error (r/atom nil)
        set-format-error! (fn [error?] (reset! format-error error?))
        set-error! (fn [err] (reset! error err))
        user-id (subscribe [:user-id])
        nickname (subscribe [:nickname] [user-id])]
    (fn []
      [:div.nickname
       (when @nickname
         [:div.current-name @nickname])
       (when @error
         [:span.error @error])
       ; TODO: check if nickname is taken while typing
       [:input.new-name
        {:class (when @format-error "error")
         :placeholder "New Nickname"
         :on-key-up
         (fn [e]
           (let [text (.. e -target -value)]
             (set-format-error! (not (valid-nickname? text)))))
         :on-key-down
         (fn [e]
           (set-error! nil)
           (let [nickname (.. e -target -value)]
             (when (and (= KeyCodes.ENTER e.keyCode)
                     (re-matches #"\S+" nickname))
               (dispatch! :set-nickname
                          [nickname
                           (fn [err] (set-error! err))]))))}]])))

(defn password-view
  []
  (let [new-pass (r/atom "")
        pass-confirm (r/atom "")
        response (r/atom nil)]
    (fn []
      [:form.password
       {:on-submit (fn [e]
                     (.preventDefault e)
                     (.stopPropagation e)
                     (when (and (not (string/blank? @new-pass))
                             (= @new-pass @pass-confirm))
                       (dispatch! :set-password
                                  [@new-pass
                                   (fn []
                                     (reset! response {:ok true})
                                     (reset! new-pass "")
                                     (reset! pass-confirm ""))
                                   (fn [err]
                                     (reset! response err))])))}
       (when @response
         (if-let [err (:error @response)]
           [:div.error err]
           [:div.success "Password changed"]))
       [:label "New Password"
        [:input.new-password
         {:type "password"
          :placeholder "••••••••"
          :value @new-pass
          :on-change (fn [e] (reset! new-pass (.. e -target -value)))}]]
       [:label "Confirm Password"
        [:input.new-password
         {:type "password"
          :placeholder "••••••••"
          :value @pass-confirm
          :on-change (fn [e] (reset! pass-confirm (.. e -target -value)))}]]
       [:button {:disabled (or (string/blank? @new-pass)
                               (not= @new-pass @pass-confirm))}
        "Change Password"]])))

(defn invitations-view
  [invites]
  [:div.pending-invites
   [:h2 "Invites"]
   [:ul.invites
    (for [invite invites]
      ^{:key (invite :id)}
      [:li.invite
       "Group "
       [:strong (invite :group-name)]
       " from "
       [:strong (invite :inviter-email)]
       [:br]
       [:button {:on-click
                 (fn [_]
                   (dispatch! :accept-invite invite))}
        "Accept"]
       [:button {:on-click
                 (fn [_]
                   (dispatch! :decline-invite invite))}
        "Decline"]])]])

(defn email-settings-view
  []
  (let [email-freq (subscribe [:user-preference :email-frequency])]
    (fn []
      [:div
       [:p "If you wish, you can recieve emails notifying you of threads that you"
        " haven't seen yet."]
       [:p (str "Currently getting emails " ((fnil name "never") @email-freq))]
       [:p "Recieve emails: "]
       [:select {:value @email-freq
                 :on-change (fn [e]
                              (let [v (keyword (.. e -target -value))]
                                (dispatch! :set-preference [:email-frequency v])))}
        (doall
          (for [freq [:never :weekly :daily]]
            ^{:key freq}
            [:option {:value freq} (name freq)]))]])))

(defn notification-rule-view
  [[event condition]]
  (case event
    [:tr
     [:td (case event
            :any "Any Event"
            :mention "I am mentioned"
            :tag "Message is tagged ")]
     [:td (case event
            (:any :mention)
            (str "In "
                 (if (= condition :any)
                   "any group"
                   (:name (store/id->group condition))))
            :tag [tag-pill-view condition])]
     [:td [:button {:on-click (fn [_]
                                (dispatch! :remove-notification-rule
                                           [event condition]))}
           "-"]]]))

(defn new-rule-view
  []
  (let [event (r/atom :any)
        condition (r/atom :any)
        event-tag (r/atom nil)
        groups (subscribe [:groups])
        all-tags (subscribe [:tags])
        tags (reaction (group-by :group-id @all-tags))
        default-condition (reaction (case @event
                                      (:any :mention) :any
                                      :tag (:id (first @all-tags))))]
    (fn []
      [:tr
       [:td [:select {:value @event
                      :on-change (fn [e]
                                   (reset! event (keyword (.. e -target -value)))
                                   (reset! condition @default-condition))}
             [:option {:value :any} "Any Event"]
             [:option {:value :mention} "I am mentioned"]
             [:option {:value :tag} "A message is tagged with..."]]]
       [:td
        (case @event
          (:any :mention)
          [:select {:value @condition
                    :on-change (fn [e]
                                 (let [v (.. e -target -value)]
                                   (if (= v "any")
                                     (reset! condition :any)
                                     (reset! condition (UUID. v nil)))))}
           [:option {:value :any} "Any Group"]
           (doall
             (for [group @groups]
               ^{:key (group :id)}
               [:option {:value (group :id)} (group :name)]))]

          :tag
          [:select {:value @event-tag
                    :on-change (fn [e]
                                 (let [tag-id (UUID. (.. e -target -value) nil)]
                                   (reset! condition tag-id)))}
           (doall
             (for [group-id (keys @tags)]
               ^{:key group-id}
               [:optgroup {:label (:name (store/id->group group-id))}
                (doall
                  (for [tag (get @tags group-id)]
                    ^{:key (tag :id)}
                    [:option {:value (tag :id)} (tag :name)]))]))])]
       [:td
        [:button {:on-click (fn [_]
                              (dispatch! :add-notification-rule [@event @condition]))}
         "Save"]]])))

(defn notification-rules-view
  []
  (let [rules (subscribe [:user-preference :notification-rules])]
    (fn []
      [:div
       [:table
        [:thead
         [:tr [:th "Notify me if"] [:th "In"] [:th ""]]]
        [:tbody
         (doall
           (for [rule @rules]
             ^{:key (hash rule)}
             [notification-rule-view rule]))
         [new-rule-view]]]])))

(defn notification-settings-view
  []
  (let [notifications-enabled (r/atom (notify/enabled?))]
    (fn []
      [:div
       [:p "Braid is designed to let you work asynchronously, but sometimes you "
        "want to know right away when something happens.  You can choose events "
        "that will trigger HTML notifications if you're online or emails if offline."]
       (if (notify/has-notify?)
         [:div
          (when-not @notifications-enabled
            [:button
             {:on-click
              (fn [_] (notify/request-permission
                        (fn [perm]
                          (when (= perm "granted")
                            (reset! notifications-enabled true)
                            (notify/notify {:msg "Notifications Enabled!"})))))}
             "Enable Desktop Notifications"])
          [notification-rules-view]]
         [:p "The browser you're currently using doesn't support notifications. "
          "You can enable them here for other platforms and emails though."])])))

(defn me-page-view
  []
  (let [invitations (subscribe [:invitations])]
    (fn []
      [:div.page.me
       [:div.title "Me!"]
       [:div.content
        [:h2 "Log Out"]
        [:button.logout {:on-click
                         (fn [_]
                           (dispatch! :logout nil))} "Log Out"]
        [:h2 "Update Nickname"]
        [nickname-view]
        [:h2 "Change Password"]
        [password-view]
        [:h2 "Received Invites"]
        (when (seq @invitations)
          ;TODO: render correctly
          [invitations-view @invitations])
        [:h2 "Email Digest Preferences"]
        [email-settings-view]
        [:h2 "Notification Preferences"]
        [notification-settings-view]]])))
