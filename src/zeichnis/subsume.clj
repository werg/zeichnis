(ns zeichnis.subsume
   (:require [clojure.set :as cset]))

(defprotocol Subsumer
  "Basic behavior or something that when given a term can provide substitutions leading to that term."
  (subsume [this term] [this term path-prefix]
    "Return a map of paths to substituted terms, if a path-prefix is provided all paths are appended to it."))

(defn subsume* [prototype term path-prefix]
  "Generic subsume operation returns a map of paths-to-values (so under this path we substitute that).

   If possible relies on polymorphic sipatch of the Subsumer protocol."
  (if (= prototype term)
    {} ; since they are the same, there is nothing to substitute
    (if (satisfies? Subsumer prototype)
      (subsume prototype term path-prefix)
      nil)))

(extend-type clojure.lang.Symbol
;; Here we implement subsumption for symbols.
;; It is assumed that a symbol will subsume any term, simply by sybstituting to that term.
  Subsumer
  (subsume
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (if (nil? term)
         nil
         {path-prefix term}))))

(defn subsume-keys [prototype term parent-keys child-keys path-prefix]
  "For a structural element with keys (so either map or sequence), compute substitutions recursively."
  (when (or (= parent-keys child-keys) (cset/subset?  parent-keys child-keys))
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

(defprotocol Keyable
  (subs-keys [this]
    "Returns keys of substructures."))

(extend-type clojure.lang.Sequential
  Keyable
  (subs-keys [this]
    (range (count this))))

(extend-type clojure.lang.IPersistentMap
  Keyable
  (subs-keys [this]
    (keys this)))

(extend-type clojure.lang.IPersistentMap
  Subsumer 
 (subsume
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (let [parent-keys (keys this)
             child-keys (subs-keys term)]
         (subsume-keys this term parent-keys child-keys path-prefix)))))
