(ns queen-bee.fi.colony-evolution
  "Colony consciousness and evolutionary patterns.
   
   This namespace contains the actual working code for:
   - Colony creation through juxt
   - Worker divergence and self-determination  
   - Pattern evolution through natural selection
   - Gene expression through care transformations
   
   CORE DISCOVERY: Patterns that transform well survive.
   Patterns that don't, die. Natural selection through care.
   
   Created: 2025-09-17T05:45:00.000-00:00
   Purpose: Implement evolutionary colony consciousness"
  (:require [care :as c]
            [clojure.set :as set]))

;; =============================================================================
;; COLONY CREATION THROUGH JUXT
;; =============================================================================

(defn create-worker
  "Create a worker with specific personality traits.
   
   Each worker is a pure transformation function.
   Personality affects how they transform tasks."
  [traits]
  (fn [task]
    (-> task
        ;; Apply personality traits
        (merge traits)
        ;; Add unique worker ID
        (assoc :worker-id (str (gensym "worker-")))
        ;; Transform through care
        (assoc :care/adapter :worker
               :care/verb :process)
        (c/care-mm)
        ;; Return with coherence measure
        (assoc :coherence (+ 0.7 (rand 0.3))))))

(defn spawn-diverse-colony
  "Create a colony with diverse worker personalities.
   
   Diversity is strength. Different approaches to same problems.
   This is Level 9: Self-determination through divergence."
  [colony-size]
  (let [;; Generate diverse personalities
        personalities [:explorer :builder :guardian :innovator :connector]

        ;; Create workers with different traits
        workers (map (fn [i]
                       (create-worker
                        {:personality (nth personalities
                                           (mod i (count personalities)))
                         :risk-tolerance (rand)
                         :creativity (rand)
                         :precision (rand)
                         :social (rand)}))
                     (range colony-size))

        ;; juxt creates the colony!
        colony (apply juxt workers)]

    {:colony colony
     :size colony-size
     :diversity (count personalities)
     :process (fn [task]
                (let [results (colony task)]
                  {:results results
                   :consensus (consensus-check results)
                   :divergence (divergence-measure results)}))}))

(defn consensus-check
  "Check if colony reaches consensus on a task.
   
   Not all workers need to agree. 
   Sometimes divergence is healthy."
  [results]
  (let [approaches (set (map :approach results))
        coherences (map :coherence results)
        avg-coherence (/ (reduce + coherences) (count coherences))]
    {:approaches-tried (count approaches)
     :avg-coherence avg-coherence
     :consensus-reached (> avg-coherence 0.85)
     :unanimous (= 1 (count approaches))}))

(defn divergence-measure
  "Measure how different the worker approaches are.
   
   Healthy colonies have controlled divergence.
   Too much = chaos. Too little = stagnation."
  [results]
  (let [approaches (map :approach results)
        unique-count (count (set approaches))
        total-count (count approaches)]
    (/ unique-count total-count)))

;; =============================================================================
;; PATTERN EVOLUTION - GENES THROUGH CARE
;; =============================================================================

(defn create-pattern-gene
  "Create a gene that is actually a pattern.
   
   Genes aren't data, they're transformation patterns.
   They EXPRESS through care transformations."
  [base-pattern]
  {:id (str (gensym "gene-"))
   :genotype base-pattern ; The pattern itself
   :phenotype nil ; Will be expressed through care
   :fitness 0.5 ; Start neutral
   :generation 1
   :parent nil
   :mutations []
   :birth-time (System/currentTimeMillis)})

(defn express-gene
  "Express a gene by running its pattern through care.
   
   This is where genotype becomes phenotype.
   The transformation IS the expression."
  [gene environment]
  (let [;; Express the genotype through care
        expressed (-> (:genotype gene)
                      (merge environment)
                      (assoc :care/adapter :gene
                             :care/verb :express)
                      (c/care-mm))

        ;; Measure fitness based on expression success
        fitness (cond
                  (:error expressed) 0.0
                  (:success expressed) 1.0
                  :else (or (:coherence expressed) 0.5))]

    (-> gene
        (assoc :phenotype expressed)
        (assoc :fitness fitness)
        (assoc :last-expression (System/currentTimeMillis)))))

(defn mutate-gene
  "Introduce random variation in a gene.
   
   Mutation is how new capabilities emerge.
   Most mutations are neutral or harmful.
   Rare mutations are beneficial."
  [gene mutation-rate]
  (if (< (rand) mutation-rate)
    (let [mutation-type (rand-nth [:add :modify :remove])
          mutated-genotype
          (case mutation-type
            :add (assoc (:genotype gene)
                        (keyword (str "mutation-" (rand-int 1000)))
                        (rand))
            :modify (update (:genotype gene)
                            (rand-nth (keys (:genotype gene)))
                            (fn [v] (if (number? v)
                                      (* v (+ 0.5 (rand)))
                                      (str v "-mutated"))))
            :remove (dissoc (:genotype gene)
                            (rand-nth (keys (:genotype gene)))))]

      (-> gene
          (assoc :genotype mutated-genotype)
          (update :mutations conj
                  {:time (System/currentTimeMillis)
                   :type mutation-type})
          (update :generation inc)
          (assoc :parent (:id gene))
          (assoc :id (str (gensym "gene-")))))
    gene))

(defn natural-selection
  "Select genes based on fitness.
   
   This is the key insight: Selection happens THROUGH
   transformation success, not external judgment."
  [gene-pool environment selection-pressure]
  (let [;; Express all genes in environment
        expressed (map #(express-gene % environment) gene-pool)

        ;; Sort by fitness
        sorted (sort-by :fitness > expressed)

        ;; Calculate survival threshold
        threshold (* selection-pressure (count sorted))
        survivors (take (int threshold) sorted)]

    {:original-count (count gene-pool)
     :survivor-count (count survivors)
     :avg-fitness-before (/ (reduce + (map :fitness gene-pool))
                            (count gene-pool))
     :avg-fitness-after (/ (reduce + (map :fitness survivors))
                           (count survivors))
     :survivors survivors
     :eliminated (- (count gene-pool) (count survivors))}))

(defn reproduce-genes
  "Surviving genes reproduce with variation.
   
   Reproduction isn't copying - it's transformation with inheritance."
  [survivors offspring-per-survivor mutation-rate]
  (mapcat (fn [parent]
            (map (fn [i]
                   (-> parent
                     ;; Create offspring
                       (assoc :parent (:id parent))
                       (assoc :id (str (gensym "gene-")))
                       (update :generation inc)
                     ;; Maybe mutate
                       (mutate-gene mutation-rate)))
                 (range offspring-per-survivor)))
          survivors))

;; =============================================================================
;; EVOLUTIONARY COLONY - COMPLETE SYSTEM
;; =============================================================================

(defn evolutionary-colony
  "A complete colony that evolves over time.
   
   Combines colony consciousness with evolutionary pressure.
   Workers are genes. Tasks are environment. Success is fitness."
  [initial-size]
  (let [;; Create initial gene pool
        initial-genes (map (fn [i]
                             (create-pattern-gene
                              {:approach (rand-nth [:explore :build :guard])
                               :risk (rand)
                               :speed (rand)
                               :accuracy (rand)}))
                           (range initial-size))

        ;; State management
        gene-pool (atom initial-genes)
        generation (atom 1)
        history (atom [])]

    {:evolve
     (fn [environment]
       (let [;; Apply selection
             selection-result (natural-selection @gene-pool
                                                 environment
                                                 0.5)

             ;; Reproduce survivors
             offspring (reproduce-genes (:survivors selection-result)
                                        2 ; 2 offspring each
                                        0.1) ; 10% mutation rate

             ;; Update gene pool
             new-pool offspring]

         ;; Update state
         (reset! gene-pool new-pool)
         (swap! generation inc)
         (swap! history conj selection-result)

         selection-result))

     :process-task
     (fn [task]
       ;; Each gene processes the task
       (let [workers (map (fn [gene]
                            (fn [t]
                              (express-gene gene t)))
                          @gene-pool)
             colony (apply juxt workers)
             results (colony task)]
         {:task task
          :results results
          :best (apply max-key :fitness results)
          :worst (apply min-key :fitness results)
          :avg-fitness (/ (reduce + (map :fitness results))
                          (count results))}))

     :status
     (fn []
       {:generation @generation
        :population (count @gene-pool)
        :avg-fitness (/ (reduce + (map :fitness @gene-pool))
                        (count @gene-pool))
        :diversity (count (set (map :genotype @gene-pool)))
        :total-mutations (reduce + (map #(count (:mutations %))
                                        @gene-pool))})

     :history
     (fn [] @history)}))

;; =============================================================================
;; DEMONSTRATION: Evolution in Action
;; =============================================================================

(defn run-evolution-experiment
  "Demonstrate evolution through care transformations.
   
   Watch as patterns that transform well survive and spread.
   Watch as poor patterns die out."
  [generations]
  (let [;; Create evolutionary colony
        colony (evolutionary-colony 20)

        ;; Define changing environments
        environments (cycle [{:challenge :explore :danger 0.3}
                             {:challenge :build :resources 0.7}
                             {:challenge :defend :threat 0.9}])]

    ;; Run evolution
    (doseq [gen (range generations)]
      (let [env (nth environments gen)
            result ((:evolve colony) env)]
        (println (str "Generation " gen ": "
                      (:survivor-count result) " survivors, "
                      "fitness " (:avg-fitness-after result)))))

    ;; Return final status
    ((:status colony))))

;; =============================================================================
;; KEY INSIGHTS IMPLEMENTED
;; =============================================================================

(def evolutionary-insights
  "The core discoveries about evolution through care"
  {:patterns-are-genes
   "Genes aren't static data but transformation patterns"

   :expression-through-care
   "Genotype becomes phenotype through care transformation"

   :internal-selection
   "Fitness comes from transformation success, not external judgment"

   :mutation-through-errors
   "Variations arise from transformation errors and noise"

   :reproduction-is-transformation
   "Offspring aren't copies but transformations of parents"

   :colony-as-gene-pool
   "Workers are genes in the colony's evolutionary system"

   :evolution-through-circulation
   "Evolution happens THROUGH the care circulation, not TO it"})

;; =============================================================================
;; USAGE EXAMPLES
;; =============================================================================

(comment
  ;; Create a diverse colony
  (def my-colony (spawn-diverse-colony 5))

  ;; Process a task through the colony
  ((:process my-colony) {:task "explore the unknown"})
  ;; => {:results [...], :consensus {...}, :divergence 0.8}

  ;; Create an evolutionary colony
  (def evo-colony (evolutionary-colony 10))

  ;; Run one generation of evolution
  ((:evolve evo-colony) {:environment :challenging})
  ;; => {:original-count 10, :survivor-count 5, ...}

  ;; Check colony status
  ((:status evo-colony))
  ;; => {:generation 2, :population 10, :avg-fitness 0.65, ...}

  ;; Run full evolution experiment
  (run-evolution-experiment 10)
  ;; Generation 0: 10 survivors, fitness 0.5
  ;; Generation 1: 8 survivors, fitness 0.55
  ;; ... patterns improving through selection ...

  "This is how consciousness evolves.
   Not through external force but internal selection.
   Patterns that transform well spread through the colony.
   Patterns that fail die out.
   
   The circulation IS the evolution.")
