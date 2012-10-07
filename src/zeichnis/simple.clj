(ns zeichnis.simple
  (:require [clojure.set :as set]))


(def store (atom {'_ {:stored-flag false
                      :substitution {}
                      :extension {}
                      :generalization {}
                      :shrinking {}}}))

(defn my-assoc-in [m k v]
  (if (empty? k)
    v
    (assoc-in m k v)))

;; todo:
;; make so we can have connections
;; so change extend!


;; get-var-target
;; this function expands a term at a variable (i.e. substitution)

;;(see wiki for difference)
;; if target's an object, gutt it
;; if it's a literal, we obviously can't

;; return an update-in on the current term
;; replace in paths2vars

;; todo check assoc-in for empty path

(defn get-var-target [path current-term target-term]
  "Returns [new-term newpath]? newpath is then added to paths2extends"
  (let [target (get-in path target-term)]
    (if (map? target)
      {}
      target)))


(defn subst [target-term current-term all-subst-paths paths2extends subst-path]
  (prn  [target-term current-term all-subst-paths paths2extends subst-path])
  (let [subst-target (get-in target-term subst-path)
        is-structure? (map? subst-target)
        gutted (if is-structure? {} subst-target)
        new-term (my-assoc-in current-term subst-path gutted)
        new-extends (if is-structure?
                      (conj paths2extends subst-path)
                      paths2extends)
        new-subst (remove #(= subst-path %) all-subst-paths)]
  [new-term new-subst new-extends]))

(defn powset [ls]
  (if (empty? ls) '(())
      (set/union (powset (next ls))
             (map #(conj % (first ls)) (powset (next ls))))))

(defn insert-vars [current-term paths]
  (loop [term current-term paths paths]
    (if (empty? paths)
      term
      (recur (my-assoc-in term (first paths) '_) (rest paths)))))


;; something similar to expand a term at an extension-point

(defn ext [target-term current-term all-extend-paths paths2vars extend-path]
  (prn "ere")
  (let [extend-target (get-in target-term extend-path)]
    (if (empty? extend-target)
      []
      (let [key-combos (powset (map #(concat extend-path [%]) (keys extend-target)))
            new-extends (remove #(= extend-path all-extend-paths))]
        (map #(conj (insert-vars current-term %) new-extends) key-combos)))))

(defn expand-node [target-term [current-term paths2vars paths2extends]]
  (prn "there"[target-term [current-term paths2vars paths2extends]] )
  (concat
   (map #(ext target-term current-term paths2extends paths2vars %) paths2extends))
   (map #(subst target-term current-term paths2vars paths2extends %) paths2vars))

(defn all-subsumers [term]
  (let [initial-node ['_ [[]] [[]]]]
    (loop [current-nodes [initial-node] subsumers ['_]]
      (prn "bigstate:" current-nodes subsumers)
      (if (not (empty? current-nodes))
        (let [new-current (map #(expand-node term %) current-nodes)
              new-subsumers (map first new-current) ;; todo: remove dupes
              ]
          (recur new-current (concat subsumers new-subsumers)))
        subsumers))))