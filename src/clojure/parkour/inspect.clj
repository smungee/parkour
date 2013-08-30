(ns parkour.inspect
  (:require [parkour.inspect (mapred :as mr1) (mapreduce :as mr2)]))

(defn records-seqable
  "Return `.close`-able handle to the records generated by input
format class `klass` with configuration `conf` and optional `paths`.
Result is `seq`-able; the resulting sequence contains the results of
applying `f` to a vector of each key-value tuple as it is read."
  [conf klass f & paths]
  (let [rsf (cond (mr1/input-format? klass) mr1/records-seqable
                  (mr2/input-format? klass) mr2/records-seqable
                  :else (throw (ex-info "Not an input format"
                                        {:class klass})))]
    (apply rsf conf klass f paths)))
