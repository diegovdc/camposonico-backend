(ns camposonico-backend.jdbc.protocol-extensions)


(defn run-extensions! []
  (extend-protocol clojure.java.jdbc/ISQLParameter
    clojure.lang.IPersistentVector
    (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
      (let [conn (.getConnection stmt)
            meta (.getParameterMetaData stmt)
            type-name (.getParameterTypeName meta i)]
        (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
          (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
          (.setObject stmt i v)))))

  (extend-protocol clojure.java.jdbc/IResultSetReadColumn
    java.sql.Array
    (result-set-read-column [val _ _]
      (into [] (.getArray val)))))
