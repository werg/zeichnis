(ns zeichnis.core
  (:require [clojure.set :as cset]))

;; below should be a type and the two behaviors should be two different protocols
;; TODO
(defprotocol ZeichnisPeer
  (get-termstore [this key])
  (add-termstore [this key]))


(defprotocol PartialOrder
  (all> [this item])
  (any> [this item])
  (all< [this item])
  (any< [this item])
  (has? [this item]))

(defprotocol TermStore
  (store-term [this term]))

(defprotocol Subsumer
  (subsume [this term] [this term path-prefix]))

(defn subsume* [prototype term path-prefix]
  (if (= prototype term)
    {} ; since they are the same, there is nothing to substitute
    (if (satisfies? Subsumer prototype)
      (subsume prototype term path-prefix)
      nil)))

(extend-type clojure.lang.Symbol
  Subsumer
  (subsume
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (if (nil? term)
         nil
         {path-prefix term}))))

(defn subsume-keys [prototype term parent-keys child-keys path-prefix]
  (if (or (= parent-keys child-keys) (cset/subset?  parent-keys child-keys))
    (let [ext-keys (cset/difference (set child-keys) (set parent-keys))
          exts (zipmap (map conj (repeat path-prefix) ext-keys)
                       (map get (repeat term) ext-keys))
          substs (loop [substs {} parent-keys parent-keys]
                   (if (empty? parent-keys)
                     substs
                     (let [pk (first parent-keys)]
                       (if-let [subst (subsume*
                                       (get prototype pk)
                                       (get term pk)
                                       (conj path-prefix pk))]
                         (recur (merge substs subst) (rest parent-keys))
                         nil))))]
          (if (nil? substs)
            nil
            (merge exts substs)))))

(extend-type clojure.lang.IPersistentMap
  Subsumer
  (subsume
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (let [parent-keys (keys this)
             child-keys (if (map? term)
                          (keys term)
                          (range (count term)))]
         (subsume-keys this term parent-keys child-keys path-prefix)))))

;;todo add extra path

(defn find-subsumers [prototypes term]
  (loop [subsumers '() prototypes prototypes]
    (if (empty? prototypes)
      subsumers
      (let [p (first prototypes)
            s (subsume p term)]
        (if (nil? s)
          (recur subsumers (rest prototypes))
          (recur (conj subsumers [p s]) (rest prototypes)))))))

(comment
  (zeichnis.core/find-subsumers [{:a '_} {:a {}} {:b 5}] {:a {:c 1}})
  =>
   ([{:a {}} {[:a :c] 1}] [{:a _} {[:a] {:c 1}}]))
