(ns net.drilling.authors.claude.simple-ui-start
  "Let's build this step by step together.
   Start here - this is the minimal working version."
  (:require [net.drilling.modules.care.core :as care]
            #?(:cljs [replicant.dom :as rdom])))

;; =============================================================================
;; The Simplest Possible Start
;; =============================================================================

(def molecules
  "Just functions that return hiccup. That's it."
  {:text (fn [{:keys [content]}]
           [:p content])
   
   :button (fn [{:keys [label on-click]}]
             [:button {:on {:click on-click}} label])
   
   :input (fn [{:keys [value on-change placeholder]}]
            [:input {:value value
                     :placeholder placeholder
                     :on {:input on-change}}])
   
   :card (fn [{:keys [title children]}]
           [:div.card
            [:h3 title]
            [:div children]])})

(def components
  "Components pick a molecule and feed it data."
  {:greeting (fn [{:keys [name]}]
               {:molecule :text
                :content (if (empty? name)
                           "What's your name?"
                           (str "Hello, " name "!"))})
   
   :name-input (fn [{:keys [value]}]
                 {:molecule :input
                  :value value
                  :placeholder "Enter name"
                  :on-change {:care/adapter :ui
                              :care/verb :change
                              :care/variant :name}})
   
   :submit-btn (fn [_]
                 {:molecule :button
                  :label "Update Greeting"
                  :on-click {:care/adapter :ui
                             :care/verb :update
                             :care/variant :greeting}})
   
   :app-shell (fn [{:keys [children]}]
                {:molecule :card
                 :title "Simple CARE UI"
                 :children children})})

;; =============================================================================
;; The UI State - Just a Map!
;; =============================================================================

(def ui-tree
  "This describes your UI structure.
   It's just data - components and their relationships."
  {:root {:component :app-shell
          :children [{:component :name-input
                      :data-path [:form :name]}
                     {:component :submit-btn}
                     {:component :greeting
                      :data-path [:display :name]}]}
   
   ;; The actual data
   :form {:name ""}
   :display {:name ""}})

;; =============================================================================
;; The Renderer - Turn Data into Hiccup
;; =============================================================================

(defn render-node
  "Render a single node in the tree."
  [state node]
  (let [;; Get the component function
        comp-fn (get components (:component node))
        
        ;; Get data from the state if there's a path
        data (if-let [path (:data-path node)]
               (get-in state path)
               {})
        
        ;; Render children recursively
        children (when (:children node)
                   (map #(render-node state %) (:children node)))
        
        ;; Get molecule spec from component
        mol-spec (comp-fn data)
        
        ;; Get molecule function
        mol-fn (get molecules (:molecule mol-spec))]
    
    ;; Render the molecule with children
    (mol-fn (assoc mol-spec :children children))))

(defn render-ui
  "Main render function."
  [state]
  (render-node state (:root state)))

;; =============================================================================
;; CARE Methods - Handle Events
;; =============================================================================

(defmethod care/care-mm ["ui" "change" "name"]
  [{:keys [ui/state event] :as m}]
  ;; Update the form value
  (let [value (.. event -target -value)]
    (assoc-in m [:ui/state :form :name] value)))

(defmethod care/care-mm ["ui" "update" "greeting"]
  [{:keys [ui/state] :as m}]
  ;; Copy form value to display
  (let [name (get-in state [:form :name])]
    (assoc-in m [:ui/state :display :name] name)))

(defmethod care/care-mm ["ui" "render" "default"]
  [{:keys [ui/state] :as m}]
  ;; Generate hiccup from current state
  (assoc m :ui/hiccup (render-ui state)))

;; =============================================================================
;; Wire it Together
;; =============================================================================

#?(:cljs
   (defn start!
     "Start the UI system."
     [element]
     (let [;; Our state atom
           state-atom (atom ui-tree)
           
           ;; Helper to render current state
           do-render! (fn []
                        (let [hiccup (render-ui @state-atom)]
                          (rdom/render element hiccup)))]
       
       ;; Set up REPLICANT to dispatch CARE maps
       (rdom/set-dispatch!
         (fn [event-data actions]
           ;; actions will be our CARE maps from the UI
           (doseq [care-map actions]
             (when (map? care-map)
               (let [result (care/care-mm
                              (merge care-map
                                {:ui/state @state-atom
                                 :event (:replicant/dom-event event-data)}))]
                 ;; Update state if changed
                 (when-let [new-state (:ui/state result)]
                   (reset! state-atom new-state)))))))
       
       ;; Watch state and re-render on changes
       (add-watch state-atom ::render
         (fn [_ _ _ _] (do-render!)))
       
       ;; Initial render
       (do-render!)
       
       ;; Return the system for REPL exploration
       {:state state-atom
        :render do-render!})))

;; =============================================================================
;; How to Use This
;; =============================================================================

(comment
  ;; In ClojureScript REPL:
  
  ;; 1. Start the UI
  (def ui-system (start! (js/document.getElementById "app")))
  
  ;; 2. Interact with it in the browser!
  
  ;; 3. Explore from REPL:
  @(:state ui-system)
  
  ;; 4. Update state from REPL:
  (swap! (:state ui-system) assoc-in [:form :name] "REPL User")
  ;; Watch it update!
  
  ;; 5. Add new components:
  (def components 
    (assoc components
      :new-thing
      (fn [data]
        {:molecule :text
         :content "I'm new!"})))
  
  ;; The key insight: It's all just maps and functions!
  )