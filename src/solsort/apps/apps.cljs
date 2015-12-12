(ns solsort.apps.apps
  (:require-macros  [cljs.core.async.macros :refer  [go go-loop alt!]])
  (:require
    [solsort.util :refer  [route <ajax log]]
    [solsort.misc :refer  [<seq<!]]
    [reagent.core :as reagent :refer  []]
    [solsort.misc :refer  [js-seq]]
    [clojure.string :refer [replace split blank?]]
    [cljs.core.async :refer  [>! <! chan put! take! timeout close! pipe]]))

(defn name->kw [o] (keyword (str (.-nodeName o))))
(defn dom->clj [dom]
  (case (.-nodeType dom)
    ((.-DOCUMENT_NODE dom) (.-ELEMENT_NODE dom))
    (let [tag (name->kw dom)
          children (map dom->clj (js-seq (.-children dom)))
          children (if (empty? children)
                     (if (blank? (.-textContent dom))
                       []
                       [(str (.-textContent dom))])
                     children)
          attrs (into {} (map (fn [o] [(name->kw o) (.-textContent o)]))
                      (js-seq (or (.-attributes dom) [])))]
      {:tag tag
       :attrs attrs
       :children children})
    (.-TEXT_NODE dom) (str (.-textContent dom))))


(defn xml-find [p xml]
  (if (p xml) xml (some #(xml-find p %) (:children xml))))
(defn xml-find-child [p xml] (some #(xml-find p %) (:children xml)))
(defn xml->sxml [xml] 
  (if (:tag xml)
    [(:tag xml) (:attrs xml) (map xml->sxml (:children xml))]
    xml))

(defn conf-id [o]
  (if (= :preference (:tag o))
    (:name (:attrs o))
    (:tag o)))

(defn entry [id]
  (go 
     (let [config-xml (<! (<ajax (str id "/config.xml") :result "text"))
           config-dom (.parseFromString  (js/DOMParser.) config-xml "application/xml")
           config (dom->clj config-dom)
           widget (first (:children config))   
           name (:id (:attrs widget))
           version (:version (:attrs widget))
           config-elems (into {} (map (fn [e] [(conf-id e) e]) (:children widget)))
           title (first (:children (:name config-elems)))  
           description (first (:children (:description config-elems)))  
           icon (str id "/"  (:src (:attrs (:icon config-elems))))
           orientation (:value (:attrs (get config-elems "orientation")))
           path ()
           ]
    [:a
     {:href (str id "/index.html")
      :style
      {:text-color :black
       :display :block
       :text-decoration :none
       :max-width 800
       :vertical-align :top
       :margin :auto
       }
      }
     [:img {:src icon :width 100 :height 100 :style {:float :left :margin 10}}]
     [:h1 title]
     (into [:div]
           (interpose [:br] (split description "\n"))
           )
     [:br]
     ;[:div [:small [:div [:span name] " " [:span version]]]]
     ;[:div "orientation: " (str orientation)]
     ;[:br]
     ;[:pre [:small config-xml]]
     [:hr {:style {:clear :both}}]
     ])))

(defn main []
  (go
  (let 
    [repos-list (<! (<ajax "assets/repos.lst" :result "text"))
     repos-list (split repos-list "\n")
     repos-list (map #(replace % #".*/" "") repos-list)]
    (into
      [:div
       [:h1 "Solsort Apps and Widgets"]]
      (<! (<seq<! (map entry repos-list))))) ))
(go (reagent/render-component (<! (main)) js/document.body))  

(log (interpose "foo" [1 2 3]))
