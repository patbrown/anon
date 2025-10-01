# CARE-Driven UI Architecture

## The Core Insight

UI is just data transformation:
1. **State** → transforms via tags → **UI Description** (hiccup)
2. **Events** → transform state → **New State** → back to step 1

No framework. No complex event systems. Just maps transforming into hiccup, and events transforming the maps.

## The Three Layers

### 1. UI State (The Map)
```clojure
{:ui/hierarchy {:window/main {:component :window
                              :children [:app/main]}}
 :ui/data {:user/name "Alice"
           :depth/current 14320}
 :tag-registry {:render/window (fn [m] ...)
               :render/app (fn [m] ...)}
 :care/traits [:trait/with-tags?]}
```

### 2. Render Tags (Map → Hiccup)
```clojure
(defn render-window-tag [m]
  (let [component (get-in m [:ui/hierarchy :window/main])]
    {:ui/rendered [:div.window 
                   (render-children m (:children component))]}))
```

### 3. Event Tags (Event → New Map)
```clojure
(defn handle-click-tag [m]
  (let [event (:ui/event m)]
    (-> m
        (update-in [:ui/data :click-count] inc)
        (assoc :ui/event-handled true))))
```

## The Pure CARE-UI Implementation

```clojure
(ns net.drilling.modules.care-ui
  "Pure CARE approach to UI - no frameworks, just map transformation"
  (:require [net.drilling.modules.care.core :as care]
            [net.drilling.modules.care.tags :as tags]))

;; =============================================================================
;; UI Components as Tags
;; =============================================================================

(defn create-ui-system []
  (-> (tags/create-system)
      ;; Window renders its children
      (tags/register-tag :render/window
        (fn [m]
          (let [window (get-in m [:ui/hierarchy :window/main])
                children (:children window)]
            {:ui/temp-render 
             [:div.window 
              (map #(get-in m [:ui/renders %]) children)]})))
      
      ;; App renders with data
      (tags/register-tag :render/app  
        (fn [m]
          (let [app (get-in m [:ui/hierarchy :app/main])
                user-name (get-in m [:ui/data :user/name])
                depth (get-in m [:ui/data :depth/current])]
            {:ui/renders {:app/main
                         [:div.app
                          [:h1 "Drilling Control"]
                          [:p "User: " user-name]
                          [:p "Depth: " depth "ft"]]}})))
      
      ;; Button with event
      (tags/register-tag :render/button
        (fn [m]
          (let [button (get-in m [:ui/hierarchy :button/ping])]
            {:ui/renders {:button/ping
                         [:button {:on-click 
                                  (fn [e] 
                                    (dispatch-event! :click/ping {:target button}))}
                          "Ping Server"]}})))
      
      ;; Compose the full UI
      (tags/register-tag :render/compose
        (fn [m]
          {:ui/final (get m :ui/temp-render)}))))

;; =============================================================================
;; Event Handling as Tags
;; =============================================================================

(defn with-event-tags [system]
  (-> system
      (tags/register-tag :event/click-ping
        (fn [m]
          (-> m
              (assoc-in [:ui/data :last-ping] (System/currentTimeMillis))
              (update-in [:ui/data :ping-count] inc))))
      
      (tags/register-tag :event/input-change
        (fn [m]
          (let [value (get-in m [:event/data :value])]
            (assoc-in m [:ui/data :input-value] value))))))

;; =============================================================================
;; Pure Render Function
;; =============================================================================

(defn render-ui [system]
  (-> system
      (assoc :care/traits [:trait/with-tags?]
             :care/tags {:render/app {}
                        :render/button {}
                        :render/window {}
                        :render/compose {}})
      tags/employ-tags
      :ui/final))

;; =============================================================================
;; Event Dispatch (Returns New System)
;; =============================================================================

(defn handle-event [system event-type event-data]
  (-> system
      (assoc :event/type event-type
             :event/data event-data
             :care/traits [:trait/with-tags?]
             :care/tags {(keyword "event" (name event-type)) {}})
      tags/employ-tags
      (dissoc :event/type :event/data)))
```

## The Key Innovation: Placeholders as Tags

Instead of NEXUS placeholders, we use **value tags** that extract data at render time:

```clojure
(defn create-value-tags [system]
  (-> system
      ;; Current time tag
      (tags/register-tag :value/current-time
        (fn [m]
          {:extracted-values {:current-time (System/currentTimeMillis)}}))
      
      ;; User input tag
      (tags/register-tag :value/input
        (fn [m]
          {:extracted-values {:input (get-in m [:ui/data :current-input])}}))
      
      ;; Computed value tag
      (tags/register-tag :value/depth-status
        (fn [m]
          (let [depth (get-in m [:ui/data :depth])]
            {:extracted-values 
             {:depth-status (cond
                             (< depth 5000) :shallow
                             (< depth 15000) :medium
                             :else :deep)}})))))
```

## Integration with Replicant

Replicant handles the DOM updates, we handle the data:

```clojure
(defn mount-ui! [element system]
  #?(:cljs
     (let [render! (fn [sys]
                     (let [hiccup (render-ui sys)]
                       (replicant/render element hiccup)))
           
           ;; Event handler that transforms system
           handle! (fn [event-type event-data]
                     (swap! system-atom 
                            #(handle-event % event-type event-data))
                     (render! @system-atom))]
       
       ;; Initial render
       (render! system)
       
       ;; Return unmount function
       (fn [] (replicant/unmount element)))))
```

## The Beautiful Truth

1. **UI state is a map** with hierarchy and data
2. **Rendering is tag employment** - tags transform state to hiccup
3. **Events transform the map** - returning new state
4. **Replicant handles the DOM** - we just provide hiccup
5. **No frameworks** - just map transformation

## Complete Example: Self-Updating Dashboard

```clojure
(def drilling-dashboard
  (-> (create-ui-system)
      with-event-tags
      (assoc :ui/hierarchy 
             {:window/main {:component :window
                           :children [:app/main]}
              :app/main {:component :app
                        :children [:widget/depth :widget/pressure]}
              :widget/depth {:component :depth-gauge}
              :widget/pressure {:component :pressure-gauge}}
             
             :ui/data
             {:depth/current 14320
              :pressure/current 3500}
             
             :ui/update-rate 1000)
      
      ;; Tag that updates data from sensors
      (tags/register-tag :update/sensors
        (fn [m]
          (-> m
              (update-in [:ui/data :depth/current] + (rand-int 10))
              (update-in [:ui/data :pressure/current] + (- (rand-int 20) 10)))))
      
      ;; Tag that triggers periodic updates
      (tags/register-tag :schedule/update
        (fn [m]
          #?(:cljs (js/setTimeout 
                    #(swap! system-atom 
                           (fn [s]
                             (-> s
                                 (assoc :care/tags {:update/sensors {}})
                                 tags/employ-tags)))
                    (:ui/update-rate m)))
          m))))

;; The dashboard updates itself!
```

## Why This Is Revolutionary

1. **No event buses** - Events are just map transformations
2. **No component lifecycle** - Components are just render functions  
3. **No state management** - State IS the map
4. **No framework lock-in** - It's just CARE + hiccup
5. **Perfect testability** - Pure functions all the way

The UI doesn't "run" - it discovers its visual form through tag employment, just like CDD systems discover their configuration.
