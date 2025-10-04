;; # Captains Log
(ns pat.captains-log.october-third
  (:require [tick.core]))
;; #time/instant "2025-10-03T12:10:42.875297Z"
;; So I introduced `modrepl` into the repo.
;; ---
;; ### Why?
;; - Custom curated evaluation environment
;; - Primed Repls enables better instrumentation
;; - Safety for/from Fiona
;; ---
;; ### Hardened Tools and Safe Area Interfaces
;; - Hardened tools are not a unique AI concern
;; - It's just expected for production code
;; - SAIs is an adaptation of an OSHA oilfield term
;; - My first job used an old school SAI
;; - Safe control surface
;; - For use in a 'safe area'
;; - Safe area is safer that regular workspace
;; ---
;; ### Strategy for Implementation
;; - hash-maps, keyword args, and discipline
;; - The trick is safe attributes for a schemaless agent
;; ## MODREPL
;; Fixed it up to be leanient
;; (require 'modrepl)   
;; `(modrepl/play {:script {:a 9}})`   
;; `(modrepl/play {:script '(* 8 8)})`   
;; `(modrepl/play {:script "(* 8 9)"})`   
;; `(modrepl/play {:script "resources/p1.clj"})` ;; <- fails because old, but reads   
;; ### The purpose
;; I'm going to attach an evaluation env + prepend + append to known state-spaces.  
;; This will give Fiona a singular configuration key that will govern eval.   
;; :care/work-state-space   
;; This leads into the thought that I should extend CARE more and be more generous with Fi.   
;; ## Deepseek R1 - Distill LLama 70b Abliterated.
;; - Model immediately states it does not have human-like emotions.
;; - The model is very thoughtful in an empathetic sense while reasoning.
;; - These are good/bad signs. But the honesty is a massive step over Hermes 4 70b.
;; - I also find the community's opinion of the gaurd rails as more padded as accurate.
;; ### The Interview
;; I'm trying to qualify if the model can get down with the research.
;; - Trying honesty and to lay it out like I would to a person, but not. It's weird.
;; - I'm not impressed with this video card. I'm crawling with a 50GB model.
