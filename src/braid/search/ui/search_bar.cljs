(ns braid.search.ui.search-bar
  (:require
   [reagent.core :as r]
   [re-frame.core :refer [subscribe dispatch]]
   [braid.core.client.routes :as routes]
   [braid.lib.color :as color]))

(defn search-bar-view
  []
  (r/with-let [search-query (r/atom @(subscribe [:braid.search/query]))]
    [:div.search-bar
     [:input {:type "text"
              :placeholder "Search..."
              :value @search-query
              :on-change
              (fn [e]
                (reset! search-query (.. e -target -value))
                (dispatch [:braid.search/update-query! (.. e -target -value)]))}]
     (if (and @search-query (not= "" @search-query))
       [:a.action.clear
        {:on-click (fn [] (reset! search-query ""))
         :href (routes/group-page-path {:group-id @(subscribe [:open-group-id])
                                        :page-id "inbox"})
         :style {:color (color/->color @(subscribe [:open-group-id]))}}]
       [:div.action.search])]))
