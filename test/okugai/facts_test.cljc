(ns okugai.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [okugai.facts :as facts]))

(deftest outdoor-ad-permit-applies-to-everything
  (doseq [m [:utility-pole :billboard :board :wall :screen :banner]]
    (is (contains? (:required (facts/applicable {:medium m})) :outdoor-ad-permit)
        (str m))))

(deftest building-code-88-turns-on-above-four-metres
  (testing "4m 超なら要る"
    (is (contains? (:required (facts/applicable {:medium :billboard :height-m 5.2}))
                   :building-code-88)))
  (testing "4m 以下なら要らない"
    (is (contains? (:not-applicable (facts/applicable {:medium :billboard :height-m 3.0}))
                   :building-code-88)))
  (testing "**高さ不明を『不要』に倒さない** — 違法着工になる"
    (let [a (facts/applicable {:medium :billboard})]
      (is (contains? (:undetermined a) :building-code-88))
      (is (not (contains? (:not-applicable a) :building-code-88))))))

(deftest fire-prevention-district-rule
  (testing "防火地域の建物付帯は屋上/壁面なら高さ不問で 64 条"
    (is (contains? (:required (facts/applicable {:medium :rooftop
                                                 :fire-prevention-district? true}))
                   :building-code-64)))
  (testing "防火地域でなければ 64 条は要らない"
    (is (contains? (:not-applicable (facts/applicable {:medium :rooftop
                                                       :fire-prevention-district? false}))
                   :building-code-64)))
  (testing "防火地域かどうか不明なら undetermined"
    (is (contains? (:undetermined (facts/applicable {:medium :rooftop}))
                   :building-code-64))))

(deftest road-occupancy-follows-the-overhang-not-the-medium
  (is (contains? (:required (facts/applicable {:medium :utility-pole :overhangs-road? true}))
                 :road-occupancy))
  (is (contains? (:not-applicable (facts/applicable {:medium :utility-pole :overhangs-road? false}))
                 :road-occupancy))
  (is (contains? (:undetermined (facts/applicable {:medium :utility-pole}))
                 :road-occupancy)))

(deftest expressway-facility-is-always-in-scope
  (is (contains? (:required (facts/applicable {:medium :expressway-service-area
                                               :expressway-facility? true}))
                 :expressway))
  (testing "沿道は隣接判定が要る"
    (is (contains? (:undetermined (facts/applicable {:medium :expressway-roadside}))
                   :expressway))))

(deftest undetermined-blocks-by-default
  (is (true? (facts/undetermined? {:medium :billboard})))
  (is (false? (facts/undetermined? {:medium :board :overhangs-road? false}))))

(deftest jurisdiction-coverage-is-honest
  (let [c (facts/coverage)]
    (is (= ["JPN"] (:jurisdictions c)))
    (is (= 6 (:regulations c)))
    (is (re-find #"創作してはならず" (:note c)))))

(deftest required-evidence-is-medium-aware
  (testing "電柱に工作物確認済証を要求しない（88 条の trigger を持たない媒体）"
    (let [need (facts/required-evidence "JPN" {:medium :utility-pole :overhangs-road? false})]
      (is (not (some #{"building-code-88-record"} need)))
      (is (some #{"outdoor-ad-permit-record"} need))))
  (testing "4m 超の野立看板には要求する"
    (let [need (facts/required-evidence "JPN" {:medium :billboard :height-m 5.0
                                               :fire-prevention-district? false
                                               :expressway-adjacent? false})]
      (is (some #{"building-code-88-record"} need))))
  (testing "要否が undetermined のものも証跡を要求する —— 不明を不要に倒さない"
    (let [need (facts/required-evidence "JPN" {:medium :billboard})]
      (is (some #{"building-code-88-record"} need)))))

(deftest missing-evidence-distinguishes-empty-from-no-basis
  (is (= :no-spec-basis (facts/missing-evidence "XXX" {:medium :board} [])))
  (is (seq (facts/missing-evidence "JPN" {:medium :board} [])))
  (let [pl {:medium :utility-pole :overhangs-road? false}]
    (is (empty? (facts/missing-evidence "JPN" pl (facts/required-evidence "JPN" pl))))))

(deftest every-regulation-carries-a-source
  (doseq [[id r] facts/regulations]
    (is (seq (:reg/source-urls r)) (str id " has no source"))
    (is (seq (:reg/legal-basis r)) (str id " has no legal basis"))))
