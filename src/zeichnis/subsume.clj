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
  (let [pk (set parent-keys)
        ck (set child-keys)]
    (when (or (= pk ck) (cset/subset? pk ck))
      (let [ext-keys (cset/difference ck pk)
            exts (zipmap (map conj (repeat path-prefix) ext-keys)
                         (map get (repeat term) ext-keys))
            substs (loop [substs {} pk pk]
                     (if (empty? pk)
                       substs
                       (let [p (first pk)]
                         (if-let [subst (subsume*
                                         (get prototype p)
                                         (get term p)
                                         (conj path-prefix p))]
                           (recur (merge substs subst) (rest pk))
                           nil))))]
        (if (nil? substs)
          nil
          (merge exts substs))))))

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
