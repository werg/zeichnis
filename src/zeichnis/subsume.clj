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

(extend-type nil
  Subsumer
  (subsume [this term]
    (prn nil :subsume term)))

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
      (when (satisfies? Keyable term)
        (let [parent-keys (keys this)
              child-keys (subs-keys term)]
          (subsume-keys this term parent-keys child-keys path-prefix))))))

(defprotocol AntiUnifiable
  (anti-unify [this term] [this term path-prefix]))

(extend-type clojure.lang.Symbol
  AntiUnifiable
  (anti-unify
    ([this term path-prefix]
    {:lgg this ; should this return '_ ?
     :diff1 {}
     :diff2 (if (instance? clojure.lang.Symbol term)
              {}
              {path-prefix term})})
    ([this term]
       (anti-unify this term []))))

;; todo: this does not work for lists!!!

(defn- get-diff [hm keys path-prefix]
  (into {} (map #(vector (conj path-prefix %) (hm %)) keys)))

(defn anti-unify-keyables
  ([this term]
     (anti-unify this term []))
  ([this term path-prefix]
     (if (satisfies? Keyable term)
       (let [keys1 (set (subs-keys this))
             keys2  (set (subs-keys term))]
         (loop [ldiff1 (get-diff this (cset/difference keys1 keys2) path-prefix)
                ldiff2 (get-diff term (cset/difference keys2 keys1) path-prefix)
                inter-keys (cset/intersection keys1 keys2)
                llgg (empty this)]
           (if (empty? inter-keys)
             {:lgg llgg
              :diff1 ldiff1
              :diff2 ldiff2}
             (let [ike (first inter-keys)
                   {:keys [lgg diff1 diff2]} (anti-unify (get this ike)
                                                         (get term ike)
                                                         (conj path-prefix ike))]
               (recur (merge ldiff1 diff1)
                      (merge ldiff2 diff2)
                      (rest inter-keys)
                      (assoc llgg ike lgg))))))
       {:lgg '_
        :diff1 {path-prefix this}
        :diff2 {path-prefix term}})))

(extend clojure.lang.IPersistentMap
  AntiUnifiable
  {:anti-unify anti-unify-keyables})

(extend clojure.lang.Sequential
  AntiUnifiable
  {:anti-unify anti-unify-keyables})

(extend-type Object
  AntiUnifiable
  (anti-unify
    ([this term path-prefix]
       (if (= this term)
         {:lgg this
          :diff1 {}
          :diff2 {}}
         (if (instance? clojure.lang.Symbol term)
           {:lgg term
            :diff1 {path-prefix this}
            :diff2 {}}
           {:lgg '_
            :diff1 {path-prefix this}
            :diff2 {path-prefix term}})))
    ([this term]
       (anti-unify this term []))))

(defn compare-substs [subst1 subst2]
  (let [keys1 (set (keys subst1))
        keys2  (set (keys subst2))]
    (loop [ldiff1 (select-keys subst1 (cset/difference keys1 keys2))
           ldiff2 (select-keys subst2 (cset/difference keys2 keys1))
           inter-keys (cset/intersection keys1 keys2)
           linter {}]
      (if (empty? inter-keys)
        {:inter linter
         :diff1 ldiff1
         :diff2 ldiff2}
        (let [ike (first inter-keys)
              {:keys [lgg diff1 diff2]} (anti-unify (get subst1 ike) (get subst2 ike))]
          (recur (merge ldiff1 diff1)
                 (merge ldiff2 diff2)
                 (rest inter-keys)
                 (conj linter [ike lgg])))))))


(defn apply-subst [term subst]
  (loop [term term
         subst (seq subst)]
    (if (empty? subst)
      term
      (let [[path value] (first subst)]
        (if (and (empty? path) (symbol? term) (empty? (rest subst)))
          value
          (recur (assoc-in term path value) (rest subst)))))))