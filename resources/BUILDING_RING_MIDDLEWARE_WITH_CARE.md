# Building Ring Middleware with CARE: An Implementation Guide

This guide shows how to implement a CARE-based Ring middleware system as an alternative to ring-nexus-middleware, with complete working examples.

## Table of Contents
1. [Quick Start](#quick-start)
2. [Core Implementation](#core-implementation)
3. [Common Patterns](#common-patterns)
4. [Advanced Features](#advanced-features)
5. [Testing Strategy](#testing-strategy)
6. [Production Considerations](#production-considerations)

## Quick Start

Let's build a minimal working Ring application with CARE:

```clojure
(ns my-app.core
  (:require [ring.adapter.jetty :as jetty]
            [net.drilling.modules.care.core :as care]))

;; Step 1: Define your CARE method
(defmethod care/care-mm ["api" "hello" "world"]
  [{:keys [name] :as m}]
  (assoc m :http/response 
         {:status 200
          :body {:message (str "Hello, " (or name "World") "!")}}))

;; Step 2: Create a Ring handler that uses CARE
(defn care-handler [request]
  (let [care-map {:care/adapter :api
                  :care/verb :hello
                  :care/variant :world
                  :name (get-in request [:params :name])}
        result (care/care-mm care-map)]
    (:http/response result)))

;; Step 3: Start the server
(defn -main []
  (jetty/run-jetty care-handler {:port 3000}))

;; Visit http://localhost:3000?name=Pat
;; Returns: {"message": "Hello, Pat!"}
```

## Core Implementation

### The Complete CARE Middleware

Here's a production-ready CARE middleware implementation:

```clojure
(ns ring-care.middleware
  "CARE middleware for Ring - pure functional handlers with effects."
  (:require [net.drilling.modules.care.core :as care]
            [net.drilling.modules.care.tags :as tags]
            [clojure.tools.logging :as log]))

;; =============================================================================
;; Middleware Definition
;; =============================================================================

(defn wrap-care
  "Wrap a Ring handler to support CARE-style processing.
   
   Options:
   - :state-atom    - Atom containing application state
   - :state-key     - Key to add state snapshot to request (default :care/state)
   - :error-handler - Function to handle errors (fn [e request])
   - :interceptors  - Vector of interceptor functions
   - :enable-tags?  - Enable CARE tags processing (default true)"
  ([handler] (wrap-care handler nil {}))
  ([handler state-atom] (wrap-care handler state-atom {}))
  ([handler state-atom options]
   (let [{:keys [state-key error-handler interceptors enable-tags?]
          :or {state-key :care/state
               enable-tags? true}} options]
     (fn care-middleware
       ([request]
        (try
          ;; Add state snapshot if available
          (let [request (if state-atom
                         (assoc request state-key @state-atom)
                         request)
                
                ;; Apply interceptors
                request (reduce (fn [req interceptor]
                                 (interceptor req))
                               request
                               interceptors)
                
                ;; Call the handler
                response (handler request)]
            
            ;; Process based on response type
            (cond
              ;; CARE map response
              (and (map? response) (:care/adapter response))
              (process-care-response response request state-atom enable-tags?)
              
              ;; Vector of CARE maps (batch)
              (and (vector? response)
                   (every? #(and (map? %) (:care/adapter %)) response))
              (process-care-batch response request state-atom enable-tags?)
              
              ;; Regular Ring response
              (map? response)
              response
              
              ;; Unknown
              :else
              {:status 500
               :body {:error "Invalid handler response type"}}))
          
          (catch Exception e
            (log/error e "Error in CARE middleware")
            (if error-handler
              (error-handler e request)
              {:status 500
               :body {:error "Internal server error"}}))))
       
       ;; Async arity for Ring 1.6+
       ([request respond raise]
        (try
          (respond (care-middleware request))
          (catch Exception e
            (raise e))))))))

;; =============================================================================
;; CARE Processing
;; =============================================================================

(defn process-care-response
  "Process a single CARE map response."
  [care-map request state-atom enable-tags?]
  (let [;; Merge request data into CARE map
        enriched-map (merge care-map
                           {:request request
                            :params (:params request)
                            :headers (:headers request)
                            :session (:session request)})
        
        ;; Process with tags if enabled
        processed (if (and enable-tags? (:care/tags enriched-map))
                    (tags/employ-tags enriched-map)
                    (care/care-mm enriched-map))
        
        ;; Update state if changed
        _ (when (and state-atom (:state processed))
            (reset! state-atom (:state processed)))]
    
    ;; Extract HTTP response or build one
    (or (:http/response processed)
        {:status 200
         :body processed
         :headers {"Content-Type" "application/json"}})))

(defn process-care-batch
  "Process multiple CARE maps in sequence."
  [care-maps request state-atom enable-tags?]
  (reduce (fn [_ care-map]
            (process-care-response care-map request state-atom enable-tags?))
          nil
          care-maps))

;; =============================================================================
;; Built-in HTTP CARE Methods
;; =============================================================================

(defmethod care/care-mm ["http" "respond" "ok"]
  [{:keys [body headers] :as m}]
  (assoc m :http/response
         {:status 200
          :body body
          :headers (merge {"Content-Type" "application/json"} headers)}))

(defmethod care/care-mm ["http" "respond" "created"]
  [{:keys [body location] :as m}]
  (assoc m :http/response
         {:status 201
          :body body
          :headers {"Location" location}}))

(defmethod care/care-mm ["http" "respond" "accepted"]
  [{:keys [body] :as m}]
  (assoc m :http/response
         {:status 202
          :body body}))

(defmethod care/care-mm ["http" "respond" "no-content"]
  [m]
  (assoc m :http/response {:status 204}))

(defmethod care/care-mm ["http" "respond" "bad-request"]
  [{:keys [error] :as m}]
  (assoc m :http/response
         {:status 400
          :body {:error (or error "Bad request")}}))

(defmethod care/care-mm ["http" "respond" "unauthorized"]
  [{:keys [error] :as m}]
  (assoc m :http/response
         {:status 401
          :body {:error (or error "Unauthorized")}}))

(defmethod care/care-mm ["http" "respond" "forbidden"]
  [{:keys [error] :as m}]
  (assoc m :http/response
         {:status 403
          :body {:error (or error "Forbidden")}}))

(defmethod care/care-mm ["http" "respond" "not-found"]
  [{:keys [error] :as m}]
  (assoc m :http/response
         {:status 404
          :body {:error (or error "Not found")}}))

(defmethod care/care-mm ["http" "respond" "conflict"]
  [{:keys [error] :as m}]
  (assoc m :http/response
         {:status 409
          :body {:error (or error "Conflict")}}))

(defmethod care/care-mm ["http" "respond" "error"]
  [{:keys [error status] :as m}]
  (assoc m :http/response
         {:status (or status 500)
          :body {:error (or error "Internal server error")}}))

(defmethod care/care-mm ["http" "redirect" "temporary"]
  [{:keys [location] :as m}]
  (assoc m :http/response
         {:status 302
          :headers {"Location" location}}))

(defmethod care/care-mm ["http" "redirect" "permanent"]
  [{:keys [location] :as m}]
  (assoc m :http/response
         {:status 301
          :headers {"Location" location}}))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn care-response
  "Helper to create a CARE response map."
  ([adapter verb] (care-response adapter verb "default" {}))
  ([adapter verb variant] (care-response adapter verb variant {}))
  ([adapter verb variant data]
   (merge {:care/adapter adapter
           :care/verb verb
           :care/variant variant}
          data)))

(defn ok
  "Create a 200 OK response."
  [body]
  (care-response :http :respond :ok {:body body}))

(defn created
  "Create a 201 Created response."
  [body location]
  (care-response :http :respond :created {:body body :location location}))

(defn bad-request
  "Create a 400 Bad Request response."
  [error]
  (care-response :http :respond :bad-request {:error error}))

(defn not-found
  "Create a 404 Not Found response."
  [error]
  (care-response :http :respond :not-found {:error error}))

(defn error
  "Create an error response."
  [status message]
  (care-response :http :respond :error {:status status :error message}))
```

## Common Patterns

### Pattern 1: RESTful CRUD

```clojure
(ns my-app.handlers.users
  (:require [ring-care.middleware :as care-mw]
            [net.drilling.modules.care.core :as care]))

;; GET /users
(defmethod care/care-mm ["users" "list" "all"]
  [{:keys [care/state] :as m}]
  (let [users (get-in state [:db :users])]
    (care-mw/ok users)))

;; GET /users/:id
(defmethod care/care-mm ["users" "get" "by-id"]
  [{:keys [care/state params] :as m}]
  (let [user (get-in state [:db :users (:id params)])]
    (if user
      (care-mw/ok user)
      (care-mw/not-found "User not found"))))

;; POST /users
(defmethod care/care-mm ["users" "create" "new"]
  [{:keys [body] :as m}]
  (let [user (assoc body :id (random-uuid))]
    (-> m
        (assoc-in [:state :db :users (:id user)] user)
        (assoc :http/response (care-mw/created user (str "/users/" (:id user)))))))

;; PUT /users/:id
(defmethod care/care-mm ["users" "update" "by-id"]
  [{:keys [params body care/state] :as m}]
  (if (get-in state [:db :users (:id params)])
    (-> m
        (assoc-in [:state :db :users (:id params)] body)
        (assoc :http/response (care-mw/ok body)))
    (care-mw/not-found "User not found")))

;; DELETE /users/:id
(defmethod care/care-mm ["users" "delete" "by-id"]
  [{:keys [params care/state] :as m}]
  (if (get-in state [:db :users (:id params)])
    (-> m
        (update-in [:state :db :users] dissoc (:id params))
        (assoc :http/response {:status 204}))
    (care-mw/not-found "User not found")))

;; Router handler
(defn users-handler [request]
  (let [method (:request-method request)
        path-parts (clojure.string/split (:uri request) #"/")
        id (get path-parts 2)]
    (case [method (boolean id)]
      [:get false]   {:care/adapter :users :care/verb :list :care/variant :all}
      [:get true]    {:care/adapter :users :care/verb :get :care/variant :by-id
                      :params {:id id}}
      [:post false]  {:care/adapter :users :care/verb :create :care/variant :new
                      :body (:body request)}
      [:put true]    {:care/adapter :users :care/verb :update :care/variant :by-id
                      :params {:id id} :body (:body request)}
      [:delete true] {:care/adapter :users :care/verb :delete :care/variant :by-id
                      :params {:id id}}
      (care-mw/bad-request "Invalid request"))))
```

### Pattern 2: Authentication & Authorization

```clojure
(ns my-app.auth
  (:require [ring-care.middleware :as care-mw]
            [net.drilling.modules.care.core :as care]
            [net.drilling.modules.care.tags :as tags]))

;; Authentication tag
(defn register-auth-tags [system]
  (-> system
      (tags/register-tag :auth/authenticate
        (fn [m]
          (let [token (get-in m [:headers "authorization"])]
            (if-let [user (validate-token token)]
              (assoc m :current-user user)
              (assoc m :auth/failed true
                     :http/response (care-mw/unauthorized "Invalid token"))))))
      
      (tags/register-tag :auth/authorize
        (fn [m]
          (let [required-role (:required-role m)
                user-role (get-in m [:current-user :role])]
            (when (and required-role 
                      (not= user-role required-role)
                      (not (:auth/failed m)))
              (assoc m :auth/failed true
                     :http/response (care-mw/forbidden "Insufficient permissions"))))))))

;; Protected endpoint
(defmethod care/care-mm ["admin" "users" "list"]
  [{:keys [current-user] :as m}]
  (if current-user
    (care-mw/ok {:users (get-all-users)
                 :admin (:name current-user)})
    (care-mw/unauthorized "Authentication required")))

;; Handler with auth tags
(defn protected-handler [request]
  {:care/adapter :admin
   :care/verb :users
   :care/variant :list
   :care/tags {:auth/authenticate {}
              :auth/authorize {}}
   :required-role :admin
   :headers (:headers request)})
```

### Pattern 3: Database Transactions

```clojure
(ns my-app.db
  (:require [ring-care.middleware :as care-mw]
            [net.drilling.modules.care.core :as care]
            [next.jdbc :as jdbc]))

;; Transaction CARE method
(defmethod care/care-mm ["db" "transact" "default"]
  [{:keys [db-spec tx-fn] :as m}]
  (try
    (jdbc/with-transaction [tx db-spec]
      (let [result (tx-fn tx)]
        (assoc m :tx-result result)))
    (catch Exception e
      (assoc m :tx-error e
             :http/response (care-mw/error 500 "Database error")))))

;; Usage
(defn transfer-handler [request]
  {:care/adapter :db
   :care/verb :transact
   :care/variant :default
   :db-spec db-connection
   :tx-fn (fn [tx]
            (jdbc/execute! tx ["UPDATE accounts SET balance = balance - ? WHERE id = ?" 
                              100 (:from request)])
            (jdbc/execute! tx ["UPDATE accounts SET balance = balance + ? WHERE id = ?"
                              100 (:to request)])
            {:transferred 100})})
```

### Pattern 4: Async Operations

```clojure
(ns my-app.async
  (:require [ring-care.middleware :as care-mw]
            [net.drilling.modules.care.core :as care]
            [clojure.core.async :as async]))

;; Async CARE method
(defmethod care/care-mm ["async" "process" "job"]
  [{:keys [job-data] :as m}]
  (let [job-id (random-uuid)
        result-chan (async/chan)]
    
    ;; Start async processing
    (async/go
      (try
        (let [result (process-job job-data)]
          (async/>! result-chan {:success true :result result}))
        (catch Exception e
          (async/>! result-chan {:success false :error e}))))
    
    ;; Store channel for later retrieval
    (-> m
        (assoc-in [:state :jobs job-id] result-chan)
        (assoc :http/response (care-mw/accepted {:job-id job-id})))))

;; Check job status
(defmethod care/care-mm ["async" "status" "job"]
  [{:keys [job-id care/state] :as m}]
  (if-let [result-chan (get-in state [:jobs job-id])]
    (let [result (async/alt!!
                   result-chan ([v] v)
                   (async/timeout 100) :pending)]
      (if (= result :pending)
        (care-mw/ok {:status "processing"})
        (care-mw/ok result)))
    (care-mw/not-found "Job not found")))
```

## Advanced Features

### Feature 1: Request/Response Interceptors

```clojure
(defn logging-interceptor [request]
  (log/info "Request:" (:request-method request) (:uri request))
  request)

(defn timing-interceptor [request]
  (assoc request :start-time (System/currentTimeMillis)))

(defn cors-interceptor [request]
  (assoc-in request [:headers "Access-Control-Allow-Origin"] "*"))

(def app
  (-> handler
      (care-mw/wrap-care state-atom
                        {:interceptors [logging-interceptor
                                      timing-interceptor
                                      cors-interceptor]})))
```

### Feature 2: Error Recovery

```clojure
(defn error-handler [e request]
  (log/error e "Request failed" {:uri (:uri request)
                                  :method (:request-method request)})
  (cond
    (instance? java.sql.SQLException e)
    {:status 503 :body {:error "Database unavailable"}}
    
    (instance? ValidationException e)
    {:status 400 :body {:error (.getMessage e)}}
    
    :else
    {:status 500 :body {:error "Internal server error"}}))

(def app
  (-> handler
      (care-mw/wrap-care state-atom
                        {:error-handler error-handler})))
```

### Feature 3: Content Negotiation

```clojure
(defmethod care/care-mm ["content" "negotiate" "default"]
  [{:keys [headers body] :as m}]
  (let [accept (get headers "accept")]
    (cond
      (clojure.string/includes? accept "application/json")
      (assoc m :http/response {:status 200
                               :headers {"Content-Type" "application/json"}
                               :body (json/write-str body)})
      
      (clojure.string/includes? accept "text/html")
      (assoc m :http/response {:status 200
                               :headers {"Content-Type" "text/html"}
                               :body (render-html body)})
      
      :else
      (assoc m :http/response {:status 200
                               :headers {"Content-Type" "text/plain"}
                               :body (str body)}))))
```

## Testing Strategy

### Unit Testing CARE Methods

```clojure
(ns my-app.test.handlers
  (:require [clojure.test :refer :all]
            [net.drilling.modules.care.core :as care]))

(deftest test-user-creation
  (testing "Creating a user returns 201"
    (let [result (care/care-mm {:care/adapter :users
                                :care/verb :create
                                :care/variant :new
                                :body {:name "Alice"}})]
      (is (= 201 (get-in result [:http/response :status])))
      (is (= "Alice" (get-in result [:state :db :users :name]))))))

(deftest test-user-not-found
  (testing "Getting non-existent user returns 404"
    (let [result (care/care-mm {:care/adapter :users
                                :care/verb :get
                                :care/variant :by-id
                                :params {:id "fake-id"}
                                :care/state {:db {:users {}}}})]
      (is (= 404 (get-in result [:http/response :status]))))))
```

### Integration Testing

```clojure
(ns my-app.test.integration
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [my-app.core :as app]))

(deftest test-full-request-cycle
  (testing "Full request through middleware"
    (let [request (mock/request :post "/users"
                               {:name "Bob"})
          response (app/handler request)]
      (is (= 201 (:status response)))
      (is (clojure.string/starts-with? 
            (get-in response [:headers "Location"])
            "/users/")))))
```

## Production Considerations

### 1. Connection Pooling

```clojure
(def db-pool
  (hikari/make-datasource
    {:adapter "postgresql"
     :database-name "myapp"
     :maximum-pool-size 10}))

(defmethod care/care-mm ["db" "query" "default"]
  [{:keys [query params] :as m}]
  (let [result (jdbc/execute! db-pool (cons query params))]
    (assoc m :query-result result)))
```

### 2. Rate Limiting

```clojure
(def rate-limiter (atom {}))

(defn check-rate-limit [ip]
  (let [now (System/currentTimeMillis)
        requests (get @rate-limiter ip [])]
    (< (count (filter #(> (- now %) 60000) requests)) 100)))

(defn rate-limit-interceptor [request]
  (let [ip (:remote-addr request)]
    (if (check-rate-limit ip)
      (do
        (swap! rate-limiter update ip conj (System/currentTimeMillis))
        request)
      (assoc request :rate-limited true))))
```

### 3. Monitoring

```clojure
(defn metrics-interceptor [request]
  (let [start (System/currentTimeMillis)]
    (assoc request :metrics-start start)))

(defn record-metrics [response request]
  (when-let [start (:metrics-start request)]
    (let [duration (- (System/currentTimeMillis) start)]
      (metrics/record-request-duration (:uri request) duration)
      (metrics/increment-counter (str "status." (:status response)))))
  response)
```

### 4. Health Checks

```clojure
(defmethod care/care-mm ["health" "check" "default"]
  [{:as m}]
  (let [db-ok? (check-database)
        redis-ok? (check-redis)
        healthy? (and db-ok? redis-ok?)]
    (assoc m :http/response
           {:status (if healthy? 200 503)
            :body {:healthy healthy?
                   :database db-ok?
                   :redis redis-ok?
                   :timestamp (System/currentTimeMillis)}})))
```

## Complete Example Application

Here's a small but complete application using CARE:

```clojure
(ns my-app.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring-care.middleware :as care-mw]
            [net.drilling.modules.care.core :as care]))

;; State
(def app-state (atom {:users {}}))

;; CARE Methods
(defmethod care/care-mm ["api" "users" "list"]
  [{:keys [care/state] :as m}]
  (care-mw/ok (vals (:users state))))

(defmethod care/care-mm ["api" "users" "create"]
  [{:keys [body] :as m}]
  (let [user (assoc body :id (random-uuid))]
    (-> m
        (assoc-in [:state :users (:id user)] user)
        (assoc :http/response 
               (care-mw/created user (str "/api/users/" (:id user)))))))

;; Router
(defn api-handler [request]
  (case [(:request-method request) (:uri request)]
    [:get "/api/users"]  {:care/adapter :api :care/verb :users :care/variant :list}
    [:post "/api/users"] {:care/adapter :api :care/verb :users :care/variant :create
                         :body (:body request)}
    (care-mw/not-found "Endpoint not found")))

;; App
(def app
  (-> api-handler
      (care-mw/wrap-care app-state)
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-params))

;; Server
(defn -main [& args]
  (jetty/run-jetty app {:port 3000 :join? false})
  (println "Server running on http://localhost:3000"))
```

## Summary

Building Ring middleware with CARE provides:
- **Pure handlers** - All business logic is pure functions
- **Testability** - No mocking required
- **Composability** - Mix and match CARE methods
- **Simplicity** - Everything is map transformation

The pattern scales from simple APIs to complex applications while maintaining the core principle: handlers return data, CARE transforms it, middleware handles the plumbing.

Start simple, add complexity as needed, and enjoy the benefits of functional server-side code!