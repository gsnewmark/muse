(ns muse.cats-spec
  #?(:clj
     (:require [clojure.test :refer (deftest is)]
               [clojure.core.async :refer (go <!!) :as a]
               [muse.core :as muse]
               [muse.protocol :as proto]
               [cats.core :as m])
     :cljs
     (:require [cljs.test :refer-macros (deftest is async)]
               [cljs.core.async :as a :refer (take!)]
               [muse.core :as muse]
               [muse.protocol :as proto]
               [cats.core :as m]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer (go)]))
  (:refer-clojure :exclude (run!)))

(defrecord DList [size]
  #?(:clj muse/DataSource :cljs proto/DataSource)
  (fetch [_] (go (range size)))
  #?(:clj muse/LabeledSource :cljs proto/LabeledSource)
  (resource-id [_] #?(:clj size :cljs [:DList size])))

(defrecord Single [seed]
  #?(:clj muse/DataSource :cljs proto/DataSource)
  (fetch [_] (go seed))
  #?(:clj muse/LabeledSource :cljs proto/LabeledSource)
  (resource-id [_] #?(:clj seed :cljs [:Single seed])))

(deftest cats-api
  (is (satisfies? proto/MuseAST (m/fmap count (muse/value (range 10)))))
  (is (satisfies? proto/MuseAST
                  (m/with-monad proto/ast-monad
                    (m/fmap count (DList. 10)))))
  (is (satisfies? proto/MuseAST
                  (m/with-monad proto/ast-monad
                    (m/bind (Single. 10) (fn [num] (Single. (inc num))))))))

(defn assert-ast [expected ast-factory]
  #?(:clj (is (= expected (muse/run!! (ast-factory))))
     :cljs (async done (take! (muse/run! (ast-factory)) (fn [r] (is (= expected r)) (done))))))

(deftest runner-macros
  #?(:clj (is (= 5 (<!! (muse/run! (m/fmap count (DList. 5)))))))
  (assert-ast 10 (fn [] (m/fmap count (DList. 10))))
  (assert-ast 43 (fn [] (m/fmap inc (muse/value 42))))
  (assert-ast 15 (fn [] (m/bind (Single. 10) (fn [num] (Single. (+ 5 num)))))))

#?(:clj
   (deftest cats-syntax
     (assert-ast 30 (fn [] (m/mlet [x (DList. 5)
                                    y (DList. 10)
                                    z (Single. 15)]
                                   (m/return (+ (count x) (count y) z)))))))
