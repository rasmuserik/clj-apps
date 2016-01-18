(ns solsort.apps.apps
  (:require-macros  [cljs.core.async.macros :refer  [go go-loop alt!]])
  (:require
    [solsort.util :refer  [<ajax <seq<! js-seq]]
    [reagent.core :as reagent :refer  []]
    [clojure.string :refer [replace split blank?]]
    [cljs.core.async :refer  [>! <! chan put! take! timeout close! pipe]]))

(defn log [& args] (apply js/console.log (map clj->js args)))
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
     (let [config-xml (<! (<ajax (str "/" id "/config.xml") :result "text"))
           config-dom (.parseFromString  (js/DOMParser.) config-xml "application/xml")
           config (dom->clj config-dom)
           widget (first (:children config))   
           name (:id (:attrs widget))
           version (:version (:attrs widget))
           config-elems (into {} (map (fn [e] [(conf-id e) e]) (:children widget)))
           title (first (:children (:name config-elems)))  
           date (first (:children (:date config-elems)))  
           description (first (:children (:description config-elems)))  
           icon (str id "/"  (:src (:attrs (:icon config-elems))))
           orientation (:value (:attrs (get config-elems "orientation")))
           path ()]
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
     [:h1 title " " [:small " (" date ")"]]
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

(defn info []
  #_[:div
   [:h1 "solsort.com ApS"]
   [:ul
    [:li "Effektivt samarbejde - Vi afklarer løsningen sammen. Da jeg selv implementerer den, er der ingen ekstra mellemtrin."]
    [:li "Hurtig udviklingstakt - Det foregår typisk med koncentrerede 3-dages "
     [:a {:href "https://en.wikipedia.org/wiki/Scrum_%28development%29#Workflow"} "sprints"]
     ". Et sprint er et afgrænset tidsrum fra planlægning til kørende løsning. Ofte mandag-onsdag eller fredag-søndag. De bedste metoder fra "
     [:a {:href "https://en.wikipedia.org/wiki/Agile_software_development"} "agil udvikling"]
     ]
    [:li "Speciale: HTML5-løsninger, der kan bruges i websites og som mobil-app."]
    [:li "Open Source: du er ikke bundet til én leverandør, og kan gøre med løsningen hvad du vil."]
    [:li "Tilfredshedsgaranti - jeg véd, at jeg leverer. Hvis du ikke er fuldt tilfreds ved sprint-afslutning, er der fuld returret."]]
   [:h2 "Kontakt mig hvis du har et interessant projekt eller behov for softwareudvikling."]
   ]
  [:div
   [:h1 "App-list:"]
   
   ]
  
  )
(defn main []
  (go
  (let 
    [repos-list (<! (<ajax "assets/repos.lst" :result "text"))
     repos-list (split repos-list "\n")
     repos-list (map #(replace % #".*/" "") repos-list)]
    (into
      (info)
      (<! (<seq<! (map entry repos-list))))) ))
(go (reagent/render-component (<! (main)) js/document.body))  

(log (interpose "foo" [1 2 3]))
