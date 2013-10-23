(ns parkour.remote.basic
  {:private true}
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [parkour (conf :as conf) (mapreduce :as mr) (wrapper :as w)]
            [parkour.util :refer [doto-let]])
  (:import [clojure.lang IFn$OOLL]))

(defn require-readers
  "Require the namespaces of all `*data-readers*` vars."
  [] (doseq [[_ v] *data-readers*] (-> v .-ns ns-name require)))

(defn step-v-args
  ([conf key]
     (let [fqname (conf/get conf (str "parkour." key ".var"))
           [ns sym] (str/split fqname #"/" 2)
           ns (symbol (if-not (.startsWith ^String ns "#'") ns (subs ns 2)))
           v (do (require ns) (ns-resolve ns (symbol sym)))
           args (do (require-readers)
                    (some->> (conf/get conf (str "parkour." key ".args"))
                             (edn/read-string {:readers *data-readers*})))]
       [v args]))
  ([conf kind id]
     (step-v-args conf (str kind "." id))))

(defn raw?
  "True iff `v` is a raw task function-var."
  [v] (-> v meta ::mr/raw))

(defn task-transformer
  "Adapter for basic collection-transformation task."
  [f] (fn [context] (->> context w/unwrap (f context) (mr/sink context))))

(defn task-partitioner
  "Adapter for basic partitioning functions."
  [f]
  (if (instance? IFn$OOLL f)
    (fn ^long [key val ^long nparts]
      (let [key (w/unwrap key), val (w/unwrap val)]
        (.invokePrim ^IFn$OOLL f key val nparts)))
    (fn ^long [key val ^long nparts]
      (let [key (w/unwrap key), val (w/unwrap val)]
        (f key val nparts)))))

(defn task-fn
  "Return full task-function for function-var `v`, configuration `conf`, and
arguments `args`.  Wrap with `wrap`, unless `v` is a raw task function."
  [wrap v conf args] (cond-> (apply v conf args) (not (raw? v)) (wrap)))

(defn mapper-run
  [id context]
  (let [conf (doto (conf/ig context)
               (conf/assoc! "parkour.step" "map"))
        [v args] (step-v-args conf "mapper" id)]
    (conf/with-default conf
      ((task-fn task-transformer v conf args) context))))

(defn reducer-run
  [id context]
  (let [conf (doto-let [conf (conf/ig context)]
               (->> (conf/get conf (str "parkour.reducer." id ".step"))
                    (conf/assoc! conf "parkour.step")))
        [v args] (step-v-args conf "reducer" id)]
    (conf/with-default conf
      ((task-fn task-transformer v conf args) context))))

(defn partitioner-set-conf
  [conf]
  (let [[v args] (step-v-args conf "partitioner")]
    (conf/with-default conf
      (task-fn task-partitioner v conf args))))
