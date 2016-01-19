(ns solsort.apps.apps
  (:require-macros  [cljs.core.async.macros :refer  [go go-loop alt!]])
  (:require
    [solsort.util :refer  [<ajax <seq<! js-seq normalize-css load-style! put!close! parse-json-or-nil]]
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
     :posts []
     }))

;; # defn db-changes
(defn update-entry [o]
  (swap! db assoc :entries 
         (map
           (fn [entry] (if (= (:id entry) (:id o)) o entry))
           (:entries @db))))
;; # Initialisation
;; ## Blog posts
(defn <simple-xhr [url]
  (let [c (chan)
        xhr (js/XMLHttpRequest.)]
    (aset xhr "onreadystatechange" 
          (fn [a]
            (when (= 4 (aget xhr "readyState"))
              (put!close! c (aget xhr "responseText")))))
    (.open xhr "GET" url)
    (.send xhr)
    c))
(go
  (swap! db assoc :posts [])
  (loop [i 1]
    (let 
      [posts (<! (<simple-xhr (str "https://blog.solsort.com/wp-json/wp/v2/posts?page=" i)))
       posts (parse-json-or-nil posts)]
      (when (< 0 (.-length posts))
        (swap! db assoc :posts (into (:posts @db) posts))
        (recur (inc i))))))
;; ## App listing
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
    ))
;; # Styling
(load-style! normalize-css "style-reset")
(def entry-size 66)
(def box-margin 14)
(load-style! 
  {:body
   {:background :white
    :font-family :sans-serif}
   :.info 
   {:text-align :center
    :margin "40px 0 80px 0"
    }
   :a 
   {:text-decoration :none
    :color "#00a"
    }
   :.solsort-entry
   {:position :relative
    :display :inline-block
    :width entry-size
    :height 120
    :box-sizing :border-box
    :margin-left 0
    :margin-right box-margin
    :vertical-align :top
    :text-align :center
    :font-size (* entry-size 0.15)
    :font-weight :bold
    }
   ".apps" 
   {:display "inline-block"
    :vertical-align :top
    :box-sizing :border-box
    :border-right "1px solid #eee"
    :text-align :right
    :width "38%" 
    }
   ".blog" 
   {:display "inline-block"
    :vertical-align :top
    :box-sizing :border-box
    :text-align :left
    :width "61%" 
    }
   ".blog .post"
   {:display "inline-block"
    :text-align :left
    :vertical-align :top
    :font-size 12
    :width (- 160 box-margin)
    :margin-left box-margin
    :height 40
    :overflow "hidden"
    }
   ".date"
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


(defn render-date [date]
 [:div.date.nobr
      (nth months (js/parseInt (.slice date 5 7) 10))
      " " (.slice date 0 4) ]
  
  )
(defn entry [o]
  (let [date (or (:date o) "    -00")]
    [:a.solsort-entry {
                       ;:href (str "#app:" (:id o))
                       :href (str "/" (:id o))
                       :title (:shortdescription o)}
     [:img.icon {:src (str (:id o) "/icon.png")}]
     (render-date date)
     [:div.text (:title o)]]))

(defn post [o]
  (let [title  (aget (aget o "title") "rendered")
        date (aget o "date")
        link (aget o "link")] 
  [:a.post {:href link}
   (render-date date)
   [:div.text title]]))

(defn content []
  (log @db)
  [:div
   [:div.info
    [:h2 "Rasmus Erik Voel Jensen" ]
   [:h1 "solsort.com ApS"]
   [:div "HTML5 web/widgets/apps"]
   [:div
    [:span "+45 60703081"] " \u00a0 " 
    [:a {:href "mailto:hi@solsort.com?Subject=Hi"} "hi@solsort.com"] " "
    
    ]
   [:div
    [:a {:href "https://github.com/rasmuserik"} "GitHub"] " "
    [:a {:href "https://linkedin.com/in/rasmuserik"} "LinkedIn"] " "
    ]]
   ; TODO: should be :entries,:posts subscription instead of db
   [:div.apps
    (into [:div ] (map entry (:entries @db)))]
   [:div.blog
    (into [:div ] (map post (:posts @db))) ]
   
   ]
  )

(go (reagent/render-component [content] js/document.body))  

