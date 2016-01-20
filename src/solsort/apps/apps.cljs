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

;; # db-changes
(defn update-route []
  (swap! db assoc :route
         (string/split (.slice js/location.hash 1) #"[/:]")))
(defn update-entry [o]
  (swap! db assoc :entries 
         (map
           (fn [entry] (if (= (:id entry) (:id o)) o entry))
           (:entries @db))))
;; # Events
(defn set-route [& route]
  (fn [] (aset js/location "hash" (string/join ":" route))))
(defn hide-elem [event]
  (aset (-> event .-target .-style) "display" "none"))
;; # Initialisation
(defonce initialisation
  (do
    (.initializeTouchEvents js/React true)
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

;; ## Routing
(aset js/window "onhashchange" update-route)
(update-route)
;; ## /Initialisation
true
))
;; # Styling
(load-style! normalize-css "style-reset")
(def entry-size 66)
(def box-margin 14)
(def font-size 11)
(def weight :normal)
(load-style! 
  {:body
   {:background :white
    :font-family :sans-serif}

   :.info 
   {:text-align :center
    :margin "60px 0 60px 0" }

   "h1"
   {:font-weight :normal}

   :a 
   {:text-decoration :none
    :color "#00a"}

   ".date"
   {:opacity "0.4"
    :font-size "80%"
    :font-weight :normal}

   :.nobr {:white-space :nowrap}

   :.clear
   {:clear :both}

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
    :font-size font-size
    :font-weight weight
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
    :font-size font-size
    ;:font-size 12
    :font-weight weight
    :width (- 160 box-margin)
    :margin-left box-margin
    :padding-bottom (* 0.5 box-margin)
    :overflow "hidden"
    }
   ".solsort-entry img"
   {:width entry-size
    :height entry-size
    :border-radius box-margin
    }

   :.overlay
   {:position :absolute
    :top 0
    :left 0
    :width "100%"
    :min-height "100%"
    :text-align :center
    }
   ".overlay .app"
   { :display :inline-block
    :padding 20
    :margin-top 20
    :text-align :left
    :box-shadow "4px 4px 12px rgba(0,0,0,0.5)"
    :background "rgba(255,250,240,0.95)" }
   ".overlay .app .icon"
   {:width (* 2 entry-size)
    :height (* 2 entry-size)
    :border-radius  (* 2 box-margin)
    :float "left"
    :text-align :left }
   :.device
   {:transform "scale(0.6)"
    :background :black
    :display :inline-block
    :border-radius 10
    :padding "15px 15px 30px 15px"
    :box-shadow 
    "inset 4px 4px 4px white,
     inset -2px -2px 2px white,
     3px 3px 12px rgba(0,0,0,0.5) 
    "
    }
   :iframe.landscape
   {:width 480
    :height 320
    :border :none 
    :outline "1px white"}

   :iframe.portrait
   {:width 320 
    :height 480 
    :border "1px solid #888"}

   :.screenshot 
   {:clear :both 
    :max-height 300
    :max-width 300
    :margin-top 20
    :box-shadow "4px 4px 12px rgba(0,0,0,0.5)"
    }
   }
  "basic-style"
  )
;; #
(log 'here)

(defn render-date [date]
  (let [date (or date "    -00")]
    (log "date" date)
  [:div.date.nobr
   (nth months (js/parseInt (.slice date 5 7) 10))
   " " (.slice date 0 4) ])

  )
(defn entry [o]
  (let [date (or (:date o) "    -00")]
    [:a.solsort-entry {
                       :href (str "#app:" (:id o))
                       :on-click (set-route "app" (:id o))
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

(defn app-overlay []
  ;; TODO reactive route
  (let [kind (first (:route @db))
        id (second (:route @db)) 
        o (first (filter #(= (:id %) id) (:entries @db))) 
        o (or o {})
        ]
    (log "x" o)
   [:div.overlay 
     [:div.app
      [:img.icon {:src (str id "/icon.png")}]
      (render-date (:date o))
      [:div.text (:title o)]
      [:div.clear]
      [:div.device
        [:div.demo
         [:iframe 
         {:class (if (= "landscape" (:orientation o)) "landscape" "portrait")  
                  :src (str id "/index.html") :width "100%" :height "100%"}]]]
     [:div
      [:img.screenshot{:src (str id "/screenshot1.jpg") :on-error hide-elem}]
     [:img.screenshot{:src (str id "/screenshot1.png") :on-error hide-elem}]
     " \u00a0 "
     [:img.screenshot{:src (str id "/screenshot2.png") :on-error hide-elem   }]
     [:img.screenshot{:src (str id "/screenshot2.jpg") :on-error hide-elem   }]]
      
      ]  
   "overlay"])
  )
(defn content []
  (log @db)
  [:div 
    {:on-click (set-route "")}
   [:div.info
    [:h2 "Rasmus\u00a0Erik Voel\u00a0Jensen" ]
    [:h1 "solsort.com ApS"]
    [:div [:strong "HTML5 web/widgets/apps"]]
    ;[:div "Writing code, creating things, solving problems"]
    [:div
     [:span "+45\u00a060703081"] " \u00a0 " 
     [:a {:href "mailto:hi@solsort.com?Subject=Hi"} "hi@solsort.com"] " "]
    [:div
     [:a {:href "https://github.com/rasmuserik"} "GitHub"] " \u00A0 "
     [:a {:href "https://linkedin.com/in/rasmuserik"} "LinkedIn"] " "]]
   [:hr]
   ; TODO: should be :entries,:posts subscription instead of db
   [:div.apps
    (into [:div ] (map entry (:entries @db)))]
   [:div.blog
    (into [:div ] (map post (:posts @db))) ]
    (if (second (:route @db)) [app-overlay] [:span])
   ]
  )

(reagent/render-component [content] js/document.body)  

