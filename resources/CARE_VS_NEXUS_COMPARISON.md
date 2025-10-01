# CARE vs NEXUS: A Friendly Guide for NEXUS Users

Hi! If you're familiar with NEXUS and wondering how CARE might replace it for REPLICANT rendering, this guide is for you. We'll explore both systems with patience and clarity, showing how they solve similar problems with different philosophies.

## Table of Contents
1. [The Core Philosophy](#the-core-philosophy)
2. [Conceptual Mapping](#conceptual-mapping)
3. [Side-by-Side Examples](#side-by-side-examples)
4. [REPLICANT Integration](#replicant-integration)
5. [Engineering Tradeoffs](#engineering-tradeoffs)
6. [Migration Path](#migration-path)
7. [When to Use Each](#when-to-use-each)

## The Core Philosophy

### NEXUS: Separation of Concerns
NEXUS elegantly separates your system into distinct phases:
- **Actions** → Pure functions that return effects
- **Effects** → Side-effect handlers  
- **Placeholders** → Late-bound values
- **System** → External state container

It's like a well-organized pipeline where data flows through predictable stages.

### CARE: Everything is Map Transformation
CARE takes a radically different approach:
- **One Multimethod** → Dispatches on `[adapter verb variant]`
- **Maps Transform Themselves** → Via tags and recursive application
- **State Lives in the Map** → No external system needed
- **Universal Pattern** → Same pattern for data ops, UI, side effects

Think of CARE as recursive origami - the map folds itself into new shapes.

## Conceptual Mapping

Here's how NEXUS concepts translate to CARE:

| NEXUS Concept | CARE Equivalent | Key Difference |
|---------------|-----------------|----------------|
| Actions | CARE methods returning effects | CARE methods can do side effects directly |
| Effects | CARE methods with side effects | No separation - it's all just CARE |
| Placeholders | Tags with late binding | Tags are more powerful - recursive transformation |
| System→State | Map carries its own state | No external system needed |
| Dispatch | `care-mm` multimethod | Single entry point for everything |
| Registry | Multimethod dispatch | No registration - just defmethod |

## Side-by-Side Examples

Let's see how common patterns look in each system:

### Example 1: Simple State Update

**NEXUS Approach:**
```clojure
;; Define the effect
(def nexus
  {:nexus/effects
   {:effects/save
    (fn [_ store path v]
      (swap! store assoc-in path v))}
   
   :nexus/actions
   {:user/login
    (fn [state user-id]
      [[:effects/save [:current-user] user-id]])}})

;; Dispatch it
(nexus/dispatch nexus store {} [[:user/login "alice"]])
```

**CARE Approach:**
```clojure
;; Define the method
(defmethod care-mm ["jam" "add" "default"] 
  [{:keys [jam/session care/args] :as m}]
  (let [[path value] args]
    (assoc-in m [:jam/session] (assoc-in session path value))))

;; Use it
(care-mm {:care/adapter :session/jam
          :care/verb :add
          :session/jam {}
          :care/args [[:current-user] "alice"]})
;; => {:session/jam {:current-user "alice"} ...}
```

### Example 2: Async Operations with Callbacks

**NEXUS Approach:**
```clojure
(def nexus
  {:nexus/effects
   {:effects/http
    (fn [{:keys [dispatch]} _ url opts]
      (-> (js/fetch url)
          (.then (fn [response]
                   (dispatch (:on-success opts) 
                            {:response response})))))}
   
   :nexus/placeholders
   {:http.res/body
    (fn [{:keys [response]}]
      (.json response))}})

;; Usage
[[:effects/http "/api/user" 
  {:on-success [[:user/loaded [:http.res/body]]]}]]
```

**CARE Approach:**
```clojure
(defmethod care-mm ["http" "fetch" "default"]
  [{:keys [care/args] :as m}]
  (let [[url opts] args]
    (-> (js/fetch url)
        (.then (fn [response]
                 ;; Apply success tags to the map
                 (employ-tags 
                   (assoc m 
                          :http/response response
                          :care/tags (:on-success opts))))))))

;; Usage with tags
{:care/adapter :http
 :care/verb :fetch
 :care/args ["/api/user" 
             {:on-success {:user/process-response {}}}]
 :tag-registry 
 {:user/process-response 
  (fn [m]
    {:user/data (-> m :http/response .json)})}}
```

### Example 3: UI Rendering with REPLICANT

**NEXUS + REPLICANT:**
```clojure
;; Component renders with event handlers as data
(defn task-item [task]
  [:li
   [:span (:task/title task)]
   [:button {:on {:click [[:task/complete (:task/id task)]]}}
    "Complete"]])

;; Wire up NEXUS for dispatch
(r/set-dispatch! #(nexus/dispatch nexus store %1 %2))

;; Define the action
(def nexus
  {:nexus/actions
   {:task/complete
    (fn [state task-id]
      [[:effects/save [:tasks task-id :status] :done]])}})
```

**CARE + REPLICANT:**
```clojure
;; Component renders with CARE maps as event data
(defn task-item [task]
  [:li
   [:span (:task/title task)]
   [:button {:on {:click {:care/adapter :task/ndb
                         :care/verb :complete
                         :care/args [(:task/id task)]}}}
    "Complete"]])

;; Wire up CARE for dispatch
(r/set-dispatch! 
  (fn [dispatch-data care-map]
    (care care-map dispatch-data)))

;; Define the method
(defmethod care-mm ["task" "complete" "default"]
  [{:keys [care/args] :as m}]
  (let [[task-id] args]
    (update-in m [:user/task task-id :status] (constantly :done))))
```

## REPLICANT Integration

Both systems work beautifully with REPLICANT, but with different flavors:

### NEXUS Integration
- Events are **action vectors** `[:action/type & args]`
- Clean separation between UI and logic
- Dispatch through centralized `nexus/dispatch`
- Placeholders handle DOM event data elegantly

### CARE Integration  
- Events are **CARE maps** with adapter/verb/variant
- UI components can carry transformation logic via tags
- Maps flow through and transform themselves
- Event data merges directly into the CARE map

## Engineering Tradeoffs

Let's be honest about the tradeoffs:

### NEXUS Strengths
✅ **Clear separation of concerns** - Easy to reason about phases
✅ **Familiar to Redux/re-frame users** - Similar mental model
✅ **Explicit data flow** - You can trace the path
✅ **Built-in dev tools** - Dataspex integration out of the box
✅ **Battle-tested** - Used in production by its authors

### NEXUS Challenges
⚠️ **More concepts to learn** - Actions, effects, placeholders, etc.
⚠️ **Registration boilerplate** - Need to register handlers
⚠️ **External state management** - System lives outside

### CARE Strengths
✅ **Single concept** - Just one multimethod to understand
✅ **Self-contained** - Maps carry everything they need
✅ **Infinitely extensible** - Just add more defmethods
✅ **Recursive power** - Tags enable self-transformation
✅ **No registration** - defmethod is all you need

### CARE Challenges
⚠️ **Different mental model** - Recursive thinking takes adjustment
⚠️ **Everything in the map** - Can get large
⚠️ **Less tooling** - Need to build your own observability
⚠️ **Young pattern** - Less battle-tested than NEXUS

## Migration Path

If you want to try CARE while keeping NEXUS:

### Step 1: CARE as NEXUS Effect
```clojure
;; Add CARE as a NEXUS effect
(def nexus
  {:nexus/effects
   {:effect/care 
    (fn [_ system care-map]
      (let [result (care-mm (merge care-map @system))]
        (reset! system result)))}})

;; Now you can dispatch CARE maps as NEXUS effects
[[:effect/care {:care/adapter :user
                :care/verb :login
                :care/args ["alice"]}]]
```

### Step 2: Gradual Migration
Start with new features in CARE while keeping existing NEXUS code:
- Data operations → CARE's JAM/NDB operations
- New UI components → CARE's rendering methods
- Keep existing NEXUS actions running

### Step 3: Full CARE
Once comfortable, you can go full CARE:
- Replace `nexus/dispatch` with `care-mm`
- Convert actions to CARE methods
- Use tags instead of placeholders

## When to Use Each

### Choose NEXUS when:
- You want clear **separation of concerns**
- Your team knows **Redux/re-frame patterns**
- You need **production-ready tooling** today
- You prefer **explicit over implicit**
- You want a **proven solution**

### Choose CARE when:
- You value **simplicity over familiarity**
- You want **maximum flexibility**
- You're comfortable with **recursive patterns**
- You prefer **data over registration**
- You want to **experiment with new ideas**

## Final Thoughts

Both NEXUS and CARE are excellent choices for REPLICANT rendering. NEXUS gives you a production-ready, well-structured system with clear boundaries. CARE offers a radically simple approach where everything is just map transformation.

The beauty is that you don't have to choose immediately. You can run both side by side, experiment, and see what feels right for your project. 

Remember: the best system is the one that makes your team productive and your code maintainable. Both NEXUS and CARE can achieve that - they just take different paths to get there.

## Questions to Consider

As you explore these options, ask yourself:
1. **How important is tooling?** NEXUS has it, CARE needs building
2. **How complex is your state?** NEXUS separates it, CARE embeds it
3. **How many developers?** NEXUS is more familiar, CARE needs learning
4. **How much flexibility?** CARE is infinite, NEXUS is structured

The choice is yours, and there's no wrong answer. Both systems represent thoughtful approaches to the same fundamental problem: managing complexity in user interfaces.

Happy coding! 🚀
