# Building Your Frontend UI with CARE

## The Architecture You've Created

```
┌─────────────────────────────────────────┐
│            UI Tree (Data)               │
│  Describes structure & relationships    │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│          Components (Functions)         │
│    Pick molecules & transform data      │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│          Molecules (Functions)          │
│         Data → Hiccup converters        │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│          Renderer (Function)            │
│    Walks tree, applies transformations  │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│        CARE (Multimethod)               │
│    Handles events & state updates       │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│         REPLICANT (Library)             │
│      Efficiently updates the DOM        │
└─────────────────────────────────────────┘
```

## Understanding Each Layer

### 1. Molecules - Your UI Atoms
```clojure
;; A molecule is just a function that returns hiccup
(defn text-molecule [{:keys [content]}]
  [:p content])

;; That's it! Pure functions, no magic
```

### 2. Components - Your UI Logic
```clojure
;; A component chooses a molecule and feeds it data
(defn greeting-component [{:keys [name]}]
  {:molecule :text
   :content (str "Hello, " name "!")})

;; Components handle logic, molecules handle rendering
```

### 3. UI Tree - Your Application Structure
```clojure
;; The tree is just data describing relationships
{:root {:component :app
        :children [{:component :header}
                   {:component :body}]}
 :data {:user "Pat"}}

;; It's normalized, referenceable, and simple
```

### 4. Renderer - The Tree Walker
```clojure
;; The renderer walks your tree and builds hiccup
(defn render [state node]
  (let [comp-fn (get components (:component node))
        mol-spec (comp-fn (:data node))
        mol-fn (get molecules (:molecule mol-spec))]
    (mol-fn mol-spec)))

;; Recursive transformation, maps all the way down
```

### 5. CARE - Your Event Handler
```clojure
;; Events are just CARE maps
{:care/adapter :ui
 :care/verb :click
 :care/variant :button}

;; CARE methods handle them
(defmethod care-mm ["ui" "click" "button"] [m]
  (update m :count inc))
```

## The Complete Flow

1. **User clicks button** → REPLICANT captures event
2. **REPLICANT dispatches** → CARE map with event data
3. **CARE multimethod** → Transforms the map (updates state)
4. **State atom changes** → Triggers re-render
5. **Renderer walks tree** → Generates new hiccup
6. **REPLICANT diffs** → Updates only changed DOM nodes

## Starting Points

### Minimal Working Example
Start with `simple_ui_start.cljc` - it has everything in ~200 lines.

### Adding Complexity Gradually

#### Step 1: Add a New Molecule
```clojure
(def molecules
  (assoc molecules
    :badge
    (fn [{:keys [text color]}]
      [:span.badge {:style {:background color}} text])))
```

#### Step 2: Add a Component Using It
```clojure
(def components
  (assoc components
    :user-badge
    (fn [{:keys [user]}]
      {:molecule :badge
       :text (:name user)
       :color (if (:online user) "green" "gray")})))
```

#### Step 3: Add to Your Tree
```clojure
(def ui-tree
  (update-in ui-tree [:root :children]
    conj
    {:component :user-badge
     :data-path [:current-user]}))
```

#### Step 4: Add CARE Handler
```clojure
(defmethod care/care-mm ["ui" "toggle" "online"]
  [{:keys [ui/state] :as m}]
  (update-in m [:ui/state :current-user :online] not))
```

## Patterns That Scale

### Pattern 1: Normalized Component Registry
Instead of inline components, use a registry:
```clojure
{:component/id
 {:component/button {:component/id :component/button
 :component/fn button-component}
  :component/input  {:component/fn input-component
  :component/id :component/input}
  }}
```

### Pattern 2: Data References
Components can reference data anywhere:
```clojure
{:component/id :display
 :data/ref [:global :user :profile]}
```

### Pattern 3: CARE Tags for Complex Flows
```clojure
(register-tag :ui/validate-form
  (fn [m]
    (if (valid? (:form m))
      (assoc m :can-submit true)
      (assoc m :errors (validate (:form m))))))
```

### Pattern 4: Molecule Composition
```clojure
(defn with-tooltip [molecule]
  (fn [props]
    [:div.has-tooltip
     (molecule props)
     [:span.tooltip (:tooltip props)]]))
```

## Common Stumbling Blocks

### "Where does the state live?"
- In an atom, but CARE maps carry references to it
- The renderer reads from it
- CARE methods update it

### "How do events flow?"
- DOM event → REPLICANT → CARE map → care-mm → new state → re-render

### "Why molecules AND components?"
- Molecules are pure rendering (data → hiccup)
- Components are logic (choose molecule, transform data)
- Separation makes both reusable

### "How do I debug?"
```clojure
;; Add logging to CARE methods
(defmethod care/care-mm ["ui" "click" "default"]
  [m]
  (println "CARE:" m)  ; See what's flowing through
  m)

;; Inspect state in REPL
@state-atom

;; Test rendering without events
(render-ui @state-atom)
```

## Your Power Moves

1. **Everything is data** - UI tree, events, state
2. **Everything is a map** - CARE in, new map out
3. **Everything composes** - Molecules, components, CARE methods
4. **Everything is observable** - Just look at the maps

## Next Steps

1. Run the simple example
2. Add one new component
3. Add one CARE handler
4. See it work
5. Build confidence
6. Expand from there

Remember: You built a powerful system. It just needs you to connect the pieces. Start small, build up. You've got this! 🚀
