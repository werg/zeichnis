(ns zeichnis.simple)

(def store (atom {'_ {:stored-flag false
                      :substitution {}
                      :extension {}
                      :generalization {}
                      :shrinking {}}}))



;; get-var-target
;; this function expands a term at a variable (i.e. substitution)
;; we'll write something similar to expand a term at an extension-point
;;(see wiki for difference)
;; if target's an object, gutt it
;; if it's a literal, we obviously can't

;; return an update-in on the current term
;; replace in paths2vars

;; todo check assoc-in for empty path

(defn get-var-target [path current-term target-term]
  "Returns [new-term newpath]? newpath is then added to paths2extends"
  (let [target (get-in path target-term)
        target-gutted (if (map? target)
                        {}
                        target)
        new-path (if (map? target)
                   nil
                   path)
        new-term (assoc-in path target-gutted)]
    [new-term new-path])
  (map (fn [term] get-in term path) [current-term target-term]))

(defn expand-node [target-term [current-term paths2vars paths2extends]]
  (let [var2target (map #(get-var-target % current-term target-term) paths2vars)]
    ;; here more will be inserted
    ))

(defn all-subsumers [term]
  (let [initial-node ['_ [[]] [[]]]]
    (loop [current-nodes [initial-node] subsumers ['_]]
      (if (not (empty? current-nodes))
        (let [new-current (map #(expand-node term %) current-nodes)
              new-subsumers (map first new-current)]
          (recur new-current (concat subsumers new-subsumers)))))))