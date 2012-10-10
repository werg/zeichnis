(ns zeichnis.simple
  (:require [clojure.set :as cljset]))

;; try running
(comment (all-subsumers {:a {:b 23} :c "me"}))
;; should give you all subsuming terms
(comment (_ {} {:a _} {:c _} {:a {}} {:c "me"} {:a {:b _}} {:a {:b 23}}) )

;; real documentation will follow
;; and de-helmarization if god is my witness

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
  ;(prn  [target-term current-term all-subst-paths paths2extends subst-path])
  (let [subst-target (get-in target-term subst-path)
        is-structure? (map? subst-target)
        gutted (if is-structure? {} subst-target)
        new-term (my-assoc-in current-term subst-path gutted)
        new-extends (if is-structure?
                      (conj paths2extends subst-path)
                      paths2extends)
        new-subst (remove #(= subst-path %) all-subst-paths)]
  [new-term new-subst new-extends]))

(defn insert-vars [current-term paths]
  (loop [term current-term paths paths]
    (if (empty? paths)
      term
      (recur (my-assoc-in term (first paths) '_) (rest paths)))))


;; something similar to expand a term at an extension-point

(defn ext [target-term current-term all-extend-paths paths2vars extend-path]
  (let [extend-target (get-in target-term extend-path)]
    (if (empty? extend-target)
      []
      (let [current-keys (keys (get-in current-term extend-path))
            target-keys (keys extend-target)
            new-keys (cljset/difference  (set target-keys) (set current-keys))
            new-extends (remove #(= extend-path %)  all-extend-paths)
            new-paths (map #(concat extend-path [%]) new-keys)]
         (map #(vector
               (my-assoc-in current-term % '_)
               (conj paths2vars %)
               new-extends)
             new-paths)))))

(defn expand-node [target-term [current-term paths2vars paths2extends]]
  (remove nil?
          (concat
           (mapcat #(ext target-term current-term paths2extends paths2vars %) paths2extends)
           (map #(subst target-term current-term paths2vars paths2extends %) paths2vars))))

(defn all-subsumers [term]
  (let [initial-node ['_ [[]] []]]
    (loop [current-nodes [initial-node] subsumers ['_]]
      (if (empty? current-nodes)
        subsumers
        (let [new-current (mapcat #(expand-node term %) current-nodes)
              new-subsumers (map first new-current) ;; todo: remove dupes
              ]
          (recur new-current (concat subsumers new-subsumers)))))))