(ns camposonico-backend.endpoints.histories.spec-test
  (:require
   [clojure.test :refer [deftest is]]
   [clojure.spec.alpha :as s]
   [camposonico-backend.endpoints.histories.spec :as hspec]
   [camposonico-backend.endpoints.histories-test-utils :refer [history]]))

(deftest play-test
  (is (every? true? (->> history
                         (filter #(= (:event_type %) "play"))
                         (map #(s/valid? ::hspec/play-event %))))))

(deftest editor-change-test
  (is (every? true? (->> history
                         (filter #(= (:event_type %) "editor_change"))
                         (map #(s/valid? ::hspec/editor-change-event %))))))

(deftest editor-eval-test
  (is (every? true? (->> history
                         (filter #(= (:event_type %) "editor_eval"))
                         (map #(s/valid? ::hspec/editor-eval-event %))))))

(deftest volume-event-test
  (is (->> history (filter #(= (:event_type %) "volume"))
           (map #(s/valid? ::hspec/volume-event %)))))

(deftest stop-event-test
  (is (->> history (filter #(= (:event_type %) "stop"))
           (map #(s/valid? ::hspec/stop-event %)))))

(deftest history-test
  (is (s/valid? ::hspec/history history)))
