(ns okugai.medium-test
  (:require [clojure.test :refer [deftest is testing]]
            [okugai.datoms :as datoms]
            [okugai.medium :as medium]))

(deftest osm-tag-values-are-the-ones-actually-observed
  (testing "2026-08-04 都心 bbox の実測分布に出た値がすべて分類できる"
    (doseq [v ["board" "billboard" "screen" "poster_box" "column" "totem" "sign"]]
      (is (some? (medium/osm-tags->medium {"advertising" v})) (str v " unclassified"))))
  (testing "電柱も同じ入口で分類できる"
    (is (= :utility-pole (medium/osm-tags->medium {"power" "pole"})))
    (is (= :utility-pole (medium/osm-tags->medium {"man_made" "utility_pole"}))))
  (testing "広告物でないタグは nil"
    (is (nil? (medium/osm-tags->medium {"amenity" "bench"})))))

(deftest selectors-cover-every-declared-osm-tag
  (let [sel (set (medium/osm-selectors (keys medium/media)))]
    (doseq [[id m] medium/media
            tag (:medium/osm m)]
      (is (contains? sel tag) (str id " selector missing " tag)))))

(deftest unobservable-media-are-declared-not-hidden
  (testing "観測では見つからない媒体を隠さない"
    ;; rooftop: 点検出から『屋上にある』は判定できない
    ;; expressway-service-area: 施設内の媒体は地図にも写真にも出ない
    ;; expressway-roadside: billboard と同一タグ（距離での後付け分類）
    ;; transit-shelter: 停留所の位置はあるが広告面の有無は書かれていない
    (is (= #{:rooftop :expressway-service-area :expressway-roadside :transit-shelter}
           medium/unobservable-media))
    (is (not (contains? medium/observable-media :rooftop))))
  (let [c (medium/coverage)]
    (is (= (count medium/media) (:media c)))
    (is (< (:observable c) (:media c)))
    (is (seq (:unobservable c)))))

(deftest every-medium-declares-its-regulatory-triggers
  (doseq [[id m] medium/media]
    (is (seq (:medium/regulatory-triggers m)) (str id " has no triggers"))
    (is (contains? (:medium/regulatory-triggers m)
                   (if (= id :expressway-service-area) :expressway :outdoor-ad-permit))
        (str id " must declare its primary permit regime"))))

(deftest tall-media-declare-the-building-code
  (testing "自立・屋上の大型媒体は 88 条（4m 超）を trigger に持つ"
    (doseq [id [:billboard :rooftop :screen :totem :expressway-roadside]]
      (is (contains? (medium/triggers id) :building-code-88) (str id)))))

(deftest mapillary-mapping-is-one-way-and-explicit
  (is (= :utility-pole (medium/mapillary-object->medium "object--support--utility-pole")))
  (is (= :billboard (medium/mapillary-object->medium "object--sign--advertisement")))
  (is (nil? (medium/mapillary-object->medium "object--not-a-thing")))
  (testing "逆写像は明示表であって :medium/mapillary からの導出ではない
            （導出だと並び順という隠れた依存で分類が決まる）"
    (is (map? medium/mapillary-canonical))
    (is (every? medium/medium? (vals medium/mapillary-canonical)))))

(deftest derived-media-do-not-get-their-own-selectors
  (testing "expressway-roadside は billboard の後付け分類 — 独自選択子を持たせると
            同じ物件が 2 件に増える"
    (is (= :billboard (:medium/derived-from (medium/describe :expressway-roadside))))
    (is (empty? (:medium/osm (medium/describe :expressway-roadside))))))

(deftest catalog-shard-carries-media-and-regulations
  (let [s (datoms/catalog-shard)]
    (is (= (count medium/media) (count (filter :medium/id s))))
    (is (seq (filter :regulation/id s)))
    (testing "規制 entity は出典 URL を必ず持つ"
      (doseq [r (filter :regulation/id s)]
        (is (seq (:regulation/source-urls r)) (str (:regulation/id r)))))
    (testing "4m 閾値が entity に出る"
      (let [r88 (first (filter #(= "building-code-88" (:regulation/id %)) s))]
        (is (= 4.0 (:regulation/threshold-height-m r88)))
        (is (seq (:regulation/penalty r88)))))))
