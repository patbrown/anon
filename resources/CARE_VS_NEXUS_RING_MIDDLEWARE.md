# CARE vs NEXUS for Ring Middleware: A Gentle Comparison

This guide explores how CARE could replace NEXUS in server-side Ring applications, comparing them through the lens of the `ring-nexus-middleware` library.

## Table of Contents
1. [The Core Problem](#the-core-problem)
2. [How Each System Solves It](#how-each-system-solves-it)
3. [Side-by-Side Examples](#side-by-side-examples)
4. [Middleware Implementation](#middleware-implementation)
5. [Engineering Tradeoffs](#engineering-tradeoffs)
6. [Migration Path](#migration-path)

## The Core Problem

Traditional Ring handlers mix pure business logic with side effects:

```clojure
;; The problem: Everything tangled together
(defn traditional-handler [db-conn]
  (fn [request]
    (let [user-id (:user-id request)
          user (db/fetch-user db-conn user-id)]  ; Side effect!
      (if (valid? user)
        (do
          (send-email! user)                      ; Side effect!
          (db/log-access! db-conn user-id)        ; Side effect!
          {:status 200 :body {:user user}})
        {:status 404 :body "Not found"}))))
```

This is hard to test, reason about, and compose. Both NEXUS and CARE solve this by separating decisions from effects.

## How Each System Solves It

### NEXUS Approach: Actions Return Effects

NEXUS handlers return vectors of actions to be executed:

```clojure
(defn nexus-handler [request]
  (let [{:keys [db]} (:nexus/state request)  ; Pure snapshot
        user-id (:user-id request)
        user (get-user db user-id)]          ; Pure lookup
    (if (valid? user)
      ;; Return actions to execute
      [[:email/send user]
       [:db/log-access user-id]
       [:http-response/ok {:user user}]]
      [[:http-response/not-found "User not found"]])))
```

### CARE Approach: Maps Transform Themselves

CARE handlers are methods that transform the request map:

```clojure
(defmethod care-mm ["http" "handle" "get-user"]
  [{:keys [user-id db] :as m}]
  (let [user (get-user db user-id)]
    (if (valid? user)
      (-> m
          (assoc :email/to user
                 :care/tags {:email/send {}
                            :db/log-access {}
                            :http/ok {}})
          tags/employ-tags)
      (assoc m :http/response {:status 404 :body "Not found"}))))
```

## Side-by-Side Examples

Let's implement common server patterns in both systems:

### Example 1: Basic CRUD Operation

**NEXUS with ring-nexus-middleware:**
```clojure
(def nexus-config
  {:nexus/system->state deref
   :nexus/effects
   {:db/save (fn [_ store entity]
               (swap! store assoc (:id entity) entity))
    :http/respond (fn [_ _ response]
                    response)}})

(defn create-user-handler [request]
  (let [user (:body request)]
    (if (valid-user? user)
      [[:db/save user]
       [:http-response/ok {:id (:id user)}]]
      [[:http-response/bad-request "Invalid user"]])))

;; Wire it up
(def app (wrap-nexus create-user-handler nexus-config store))
```

**CARE approach:**
```clojure
(defmethod care-mm ["http" "create" "user"]
  [{:keys [body db] :as m}]
  (let [user body]
    (if (valid-user? user)
      (-> m
          (assoc-in [:db (:id user)] user)
          (assoc :http/response {:status 200 
                                 :body {:id (:id user)}}))
      (assoc m :http/response {:status 400
                               :body "Invalid user"}))))

(defn wrap-care [handler]
  (fn [request]
    (let [care-map (merge request
                          {:care/adapter :http
                           :care/verb :create
                           :care/variant :user
                           :db @store})]
      (if (vector? (handler request))
        ;; Traditional handler returned actions
        (handler request)
        ;; CARE handler - transform the map
        (let [result (care-mm care-map)]
          (when-let [new-db (:db result)]
            (reset! store new-db))
          (:http/response result))))))
```

### Example 2: Async Operations with Callbacks

**NEXUS approach:**
```clojure
(def nexus-config
  {:nexus/effects
   {:service/call 
    (fn [{:keys [dispatch]} _ service-config]
      (future
        (try
          (let [result (call-service service-config)]
            (dispatch (:on-success service-config) 
                     {:result result}))
          (catch Exception e
            (dispatch (:on-failure service-config)
                     {:error e})))))
    
   :db/save (fn [_ store data]
              (swap! store merge data))}
   
   :nexus/placeholders
   {:result (fn [{:keys [result]}] result)
    :error (fn [{:keys [error]}] (.getMessage error))}})

(defn async-handler [request]
  [[:service/call 
    {:url "/api/external"
     :on-success [[:db/save [:result]]
                  [:http-response/ok [:result]]]
     :on-failure [[:http-response/error [:error]]]}]])
```

**CARE approach:**
```clojure
(defmethod care-mm ["service" "call" "async"]
  [{:keys [url on-success on-failure] :as m}]
  (future
    (try
      (let [result (call-service {:url url})]
        ;; Apply success tags
        (-> m
            (assoc :service/result result
                   :care/tags on-success)
            tags/employ-tags))
      (catch Exception e
        ;; Apply failure tags
        (-> m
            (assoc :service/error e
                   :care/tags on-failure)
            tags/employ-tags))))
  ;; Return immediately
  (assoc m :http/response {:status 202 :body "Processing"}))

(defn async-handler [request]
  {:care/adapter :service
   :care/verb :call
   :care/variant :async
   :url "/api/external"
   :on-success {:db/save {}
               :http/notify-success {}}
   :on-failure {:http/notify-error {}}})
```

### Example 3: Request Pipeline with Validation

**NEXUS approach:**
```clojure
(defn validate-and-process [request]
  (let [{:keys [db]} (:nexus/state request)
        input (:body request)]
    (cond
      (not (authorized? request))
      [[:http-response/unauthorized "Not authorized"]]
      
      (not (valid-input? input))
      [[:http-response/bad-request "Invalid input"]]
      
      :else
      [[:db/validate input]
       [:service/process input]
       [:db/save input]
       [:http-response/ok "Processed"]])))
```

**CARE approach:**
```clojure
(defmethod care-mm ["request" "pipeline" "default"]
  [{:keys [body] :as m}]
  (-> m
      (assoc :care/tags {:auth/check {}
                        :validate/input {}
                        :service/process {}
                        :db/save {}
                        :http/respond {}})
      tags/employ-tags))

;; Each tag handles its part
(register-tag :auth/check
  (fn [m]
    (when-not (authorized? m)
      (-> m
          (assoc :pipeline/stop true
                 :http/response {:status 401})
          (dissoc :care/tags)))))  ; Stop processing

(register-tag :validate/input
  (fn [m]
    (when-not (:pipeline/stop m)
      (if (valid-input? (:body m))
        m
        (assoc m :pipeline/stop true
                :http/response {:status 400})))))
```

## Middleware Implementation

### Implementing ring-nexus-middleware with CARE

Here's how we could implement the same middleware pattern with CARE:

```clojure
(ns ring-care-middleware.core
  (:require [net.drilling.modules.care.core :as care]
            [net.drilling.modules.care.tags :as tags]))

(defn wrap-care
  "Middleware that enables CARE-style handlers.
   Handlers can return either:
   - A CARE map (will be processed)
   - A vector of actions (NEXUS compatibility)
   - A Ring response map (passthrough)"
  [handler system & [{:keys [care/state-key
                            care/fail-fast?
                            care/on-error]
                     :or {state-key :care/state
                          fail-fast? true}}]]
  (fn [request]
    (let [;; Add state snapshot to request
          state-snapshot (if (instance? clojure.lang.IDeref system)
                           @system
                           system)
          enriched-request (assoc request state-key state-snapshot)
          
          ;; Call handler
          handler-result (handler enriched-request)]
      
      (cond
        ;; Traditional Ring response
        (map? handler-result)
        (if (:care/adapter handler-result)
          ;; It's a CARE map - process it
          (let [care-map (merge handler-result
                               {:request request
                                :system system})
                result (try
                         (care/care-mm care-map)
                         (catch Exception e
                           (when on-error (on-error e))
                           (if fail-fast?
                             (throw e)
                             {:http/response {:status 500
                                            :body "Internal error"}})))]
            ;; Extract response
            (or (:http/response result)
                {:status 200 :body result}))
          ;; Regular Ring response
          handler-result)
        
        ;; NEXUS-style action vectors (compatibility)
        (vector? handler-result)
        (process-nexus-actions handler-result system)
        
        ;; Unknown format
        :else
        {:status 500 :body "Invalid handler response"}))))

;; Built-in CARE methods for HTTP responses
(defmethod care/care-mm ["http" "respond" "ok"]
  [{:keys [care/args] :as m}]
  (assoc m :http/response {:status 200 :body (first args)}))

(defmethod care/care-mm ["http" "respond" "error"]
  [{:keys [care/args] :as m}]
  (let [[status message] args]
    (assoc m :http/response {:status status :body message})))

(defmethod care/care-mm ["http" "respond" "redirect"]
  [{:keys [care/args] :as m}]
  (assoc m :http/response {:status 302 
                           :headers {"Location" (first args)}}))
```

### Using the CARE Middleware

```clojure
(require '[ring-care-middleware.core :refer [wrap-care]])

;; Define your handlers with CARE
(defn user-handler [request]
  {:care/adapter :user
   :care/verb (name (:request-method request))
   :care/variant (-> request :uri (subs 1))  ; Remove leading /
   :body (:body request)
   :params (:params request)})

;; Define CARE methods
(defmethod care/care-mm ["user" "get" "profile"]
  [{:keys [params] :as m}]
  (let [user (get-user (:id params))]
    (assoc m :http/response {:status 200 :body user})))

(defmethod care/care-mm ["user" "post" "create"]
  [{:keys [body] :as m}]
  (let [user (create-user! body)]
    (assoc m :http/response {:status 201 
                            :body user
                            :headers {"Location" (str "/user/" (:id user))}})))

;; Wire it up
(def app
  (-> user-handler
      (wrap-care store)
      wrap-params
      wrap-keyword-params))
```

## Engineering Tradeoffs

### NEXUS + ring-nexus-middleware

**Strengths:**
✅ Clear action/effect separation
✅ Built-in HTTP response helpers
✅ Familiar to Redux users
✅ Excellent error boundaries
✅ Production tested

**Challenges:**
⚠️ Two-phase execution model
⚠️ Action registration overhead
⚠️ External state management
⚠️ Coupling to middleware

### CARE for Ring

**Strengths:**
✅ Single transformation model
✅ Maps flow through naturally
✅ Recursive tag power
✅ No registration needed
✅ Middleware is optional

**Challenges:**
⚠️ Different mental model
⚠️ Less ecosystem support
⚠️ Need to build conventions
⚠️ HTTP helpers not included

## Migration Path

### Option 1: CARE as NEXUS Effect

Support both patterns during migration:

```clojure
(def nexus-config
  {:nexus/effects
   {:care/transform
    (fn [_ system care-map]
      (let [result (care/care-mm (merge care-map {:system system}))]
        ;; Update system if needed
        (when-let [new-state (:system result)]
          (reset! system new-state))
        ;; Return response
        (:http/response result)))}})

;; Now you can use CARE in NEXUS handlers
(defn hybrid-handler [request]
  [[:care/transform {:care/adapter :user
                    :care/verb :create
                    :body (:body request)}]])
```

### Option 2: Gradual Method Addition

Start adding CARE methods alongside NEXUS:

```clojure
;; Week 1: Add CARE methods for new endpoints
(defmethod care/care-mm ["api" "v2" "users"] ...)

;; Week 2: Migrate simple endpoints
(defmethod care/care-mm ["api" "health" "check"] ...)

;; Week 3: Complex workflows
(defmethod care/care-mm ["api" "workflow" "process"] ...)

;; Eventually: Remove NEXUS
```

## Real-World Patterns

### Database Transactions
```clojure
;; NEXUS
[[:db/transact [{:user/id 1 :user/name "Alice"}]]
 [:http-response/ok "Created"]]

;; CARE
(assoc m :db/tx [{:user/id 1 :user/name "Alice"}]
        :care/tags {:db/commit {}
                   :http/ok {}})
```

### Authentication
```clojure
;; NEXUS
(if (authorized? request)
  [[:continue]]
  [[:http-response/unauthorized]])

;; CARE
(if (authorized? m)
  m
  (assoc m :http/response {:status 401}))
```

### Logging
```clojure
;; NEXUS
[[:log/info "Processing request"]
 [:process request]
 [:log/info "Complete"]]

;; CARE
(-> m
    (assoc :care/tags {:log/start {}
                      :process {}
                      :log/end {}})
    tags/employ-tags)
```

## Conclusion

Both NEXUS and CARE solve the fundamental problem of tangled Ring handlers. NEXUS provides a production-ready solution with clear patterns and good tooling. CARE offers a more radical simplification where everything is map transformation.

Choose NEXUS + ring-nexus-middleware when you want:
- Proven patterns
- Clear action/effect separation
- Team familiarity with Redux-style

Choose CARE when you want:
- Maximum simplicity
- Recursive transformation power
- Freedom from registration

The beauty is that you can run both patterns side by side and migrate gradually. There's no wrong choice - just different philosophies for achieving the same goal: clean, testable, composable server-side code.