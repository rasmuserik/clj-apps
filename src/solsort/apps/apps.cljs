(ns solsort.apps.apps
  (:require-macros  [cljs.core.async.macros :refer  [go go-loop alt!]])
  (:require
    [solsort.util :refer  [<ajax <seq<! js-seq normalize-css load-style!]]
    [reagent.core :as reagent :refer  []]
    [clojure.string :as string :refer [replace split blank?]]
    [cljs.core.async :refer  [>! <! chan put! take! timeout close! pipe]]))
;; # Utility functions
(def months
  (string/split
    " January February March April May June July August September October November December"
    #" "))
(defn log  [& args]
  (js/console.log  (clj->js args))
  (first args))
;; # xml-processing utilities
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

;; # Application state
(defonce db 
  (reagent/atom 
    {:entries []
     }))

;; # defn db-changes
(defn update-entry [o]
  (swap! db assoc :entries 
         (map
           (fn [entry] (if (= (:id entry) (:id o)) o entry))
           (:entries @db))))
;; # Initialisation
(defn conf-id [o]
  (if (= :preference (:tag o))
    (:name (:attrs o))
    (:tag o)))

(go
  (let 
    [entries(<! (<ajax "assets/repos.lst" :result "text"))
     entries (split entries "\n")
     entries (map 
               (fn [repo]
                 (let [id (replace repo #".*/" "")]
                   {:id id
                    :title id
                    :github repo}))
               entries)]
    (swap! db assoc :entries entries)
    (doall (for [entry entries]
             (go
               (let [id (:id entry)
                     config-xml (<! (<ajax (str "/" id "/config.xml") :result "text"))
                     config-dom (.parseFromString  (js/DOMParser.) config-xml "application/xml")
                     config (dom->clj config-dom)
                     widget (first (:children config))   
                     config-elems (into {} (map (fn [e] [(conf-id e) e]) (:children widget)))
                     ]

                 (update-entry
                   (into entry
                         {:title (first (:children (:name config-elems)))   
                          :date (first (:children (:date config-elems)))   
                          :name (:id (:attrs widget))
                          :version (:version (:attrs widget))
                          :orientation (:value (:attrs (get config-elems "orientation"))) 
                          :icon (str id "/"  (:src (:attrs (:icon config-elems))))
                          :description (first (:children (:description config-elems)))  
                          :shortdescription (first (:children (:shortdescription config-elems)))   })
                   )
                 ;(log entry title)
                 )
               )))
    (log @db)
    ))
;; # Styling
(load-style! normalize-css "style-reset")
(def entry-size 66)
(def box-margin 14)
(load-style! 
  {:body
   {:background :white
    :font-family :sans-serif}
   :.solsort-entry
   {:position :relative
    :display :inline-block
    :width entry-size
    :box-sizing :border-box
    :margin (/ box-margin 2)
    :vertical-align :top
    :text-align :center
    :font-size (* entry-size 0.15)
    :font-weight :bold
    }
   ".solsort-entry .date"
   {:opacity "0.4"
    :font-size "80%"
    :font-weight :normal}
   ".solsort-entry img"
   {:width entry-size
    :height entry-size
    :border-radius box-margin
    }
   :.nobr {:white-space :nowrap}
   }
  "basic-style"
  )
;; #


(defn xx-entry [id]
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
          shortdescription (first (:children (:shortdescription config-elems)))  
          icon (str id "/"  (:src (:attrs (:icon config-elems))))
          orientation (:value (:attrs (get config-elems "orientation")))
          path ()]
      [:div.solsort-entry {:title shortdescription}
       [:img.icon {:src icon}]
       [:div.date.nobr
        (nth months (js/parseInt (.slice date 5) 10))
        " " (.slice date 0 4) ]
       [:div.text title]])))

(defn info []
  [:div
   [:h1 "App-list:"]])

(defn main []
  (go
    (let 
      [repos-list (<! (<ajax "assets/repos.lst" :result "text"))
       repos-list (split repos-list "\n")
       repos-list (map #(replace % #".*/" "") repos-list)]
      (into
        (info)
        (<! (<seq<! (map xx-entry repos-list))))) ))

(defn entry [o]
  (let [date (or (:date o) "    -00")]
    [:div.solsort-entry {:title (:shortdescription o)}
     [:img.icon {:src (str (:id o) "/icon.png")}]
     [:div.date.nobr
      (nth months (js/parseInt (.slice date 5 7) 10))
      " " (.slice date 0 4) ]
     [:div.text (:title o)]])   

  )

(defn icons []
  (into [:div
         [:h2 "Rasmus Erik \u00a0 Voel Jensen" ]
         [:h1 "solsort.com"]
         [:div "HTML5 web/apps"]
         [:div
          [:span "Blog"] " "
          [:span "GitHub"] " "
          [:span "LinkedIn"] " "
          [:span "info@solsort.com"] " "
          [:span "+45 60703081"] " "]
         [:br]
         [:hr]]
        ; TODO: should be :entries subscription instead of db
        (map entry (:entries @db))
        )
  )
;(go (reagent/render-component (<! (main)) js/document.body))  
(go (reagent/render-component [icons] js/document.body))  

