(ns camposonico-backend.endpoints.histories.spec
  (:require [clojure.spec.alpha :as s]))

;;;; play event
(s/def ::event_type #{"play" "stop" "volume" "editor_change" "editor_eval"})
(s/def ::audio_id int?)
(s/def ::type string?)
(s/def ::timestamp int?)
(s/def ::description string?)
(s/def ::tag string?)
(s/def ::tags (s/coll-of ::tag))
(s/def ::name string?)
(s/def ::username string?)
(s/def ::src string?)
(s/def ::url string?)
(s/def ::duration number?)
(s/def ::interval number?)
(s/def ::dur number?)
(s/def ::start number?)
(s/def ::vol number?)
(s/def ::index int?)
(s/def ::opts (s/nilable (s/keys :opt-un [::index ::vol ::start ::dur])))

(s/def ::play-event (s/keys :req-un [::event_type ::audio_id ::type
                                     ::timestamp ::interval]
                            :opt-un [::description ::tags ::name ::username
                                     ::duration ::src ::opts ::url]))

;;;; stop event
(s/def ::stop-event (s/keys :req-un [::event_type ::audio_id ::type ::timestamp ::interval]))

;;;; editor change event
(s/def ::editor_id int?)
(s/def ::line int?)
(s/def ::ch int?)
(s/def ::change-event (s/keys :req-un [::line ::ch]))
(s/def ::from ::change-event)
(s/def ::to ::change-event)
(s/def ::text (s/coll-of string?))
(s/def ::removed (s/coll-of string?))
(s/def ::change (s/keys :req-un [::from ::to ::text ::removed]))

(s/def ::editor-change-event (s/keys :req-un [::event_type ::editor_id
                                              ::change ::timestamp ::interval]))
;;;; editor eval event
(s/def ::start int?)
(s/def ::end int?)
(s/def ::code string?)
(s/def ::mark (s/keys :req-in [::start ::end ::code]))

(s/def ::editor-eval-event (s/keys :req-un [::event_type ::editor_id ::mark ::timestamp ::interval]))

;;;; volume event
(s/def ::volume_level (comp number? read-string))

(s/def ::volume-event (s/keys :req-un [::event_type ::audio_id ::volume_level ::timestamp ::interval]))


;;;; history
(s/def ::history-event (s/or :play ::play-event
                             :stop ::stop-event
                             :volume ::volume-event
                             :editor-change ::editor-change-event
                             :editor-eval ::editor-eval-event))

(s/def ::history (s/coll-of ::history-event))

(comment
  (require '[camposonico-backend.endpoints.histories-test-utils :refer [history]]))
