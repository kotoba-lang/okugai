(ns okugai.facts
  "屋外広告物の掲出に関する**管轄別・媒体別の法令カタログ**。
  `denchu.facts`（電柱専用）を一般化したもの —— 電柱はここでは 1 媒体でしかない。

  `cloud-itonami-isic-7310` の `advertising.facts` と同じ規律: ここに無い管轄は
  spec-basis が **無い**（advisor が創作してよい、ではない）。カバレッジは
  `coverage` が正直に返す。

  ## 日本の構造 — 取り違えると設計が嘘になる点

  1. 屋外広告物法に基づき、**各自治体の屋外広告物条例**が許可を出す（国ではない）。
  2. 多くの条例は電柱・街路灯柱への**貼り紙・貼り札・広告旗を禁止物件**として
     列挙する。一方、所有者の許諾と条例許可を経た巻付/袖看板は許可対象として
     別に扱われる。**『電柱広告は条例で禁止』と要約しない。**
  3. **高さ4mを超える広告塔・広告板は建築基準法88条1項（令138条1項3号）の
     工作物確認申請**が要る。屋上の広告塔も広告塔部分が4m超なら対象。確認済証の
     交付前に着工すると違反（1年以下の懲役または100万円以下の罰金）。
  4. **防火地域内**で建築物の屋上に設けるもの、または高さ3mを超えるものは主要な
     部分を不燃材料に（建築基準法64条）。
  5. 道路上空・路上に出る物件は道路法の**道路占用許可**、道路上に工作物を設ける
     行為は道路交通法の**道路使用許可**。
  6. **高速道路**は道路区域内が道路管理者（NEXCO 等）の管轄で、条例ではなく媒体社
     経由。沿道（区域外）は条例の禁止区域指定や沿道ガイドラインが上乗せされうる。

  ## 高さは媒体種別からは決まらない

  同じ `:billboard` でも 3m と 6m がある。だから `okugai.medium` の
  `:regulatory-triggers` は『効きうる規制』の集合で、実際の要否は
  `applicable` に寸法・場所を渡して判定する。**寸法が不明なら
  `:undetermined` を返す** —— 不明を『不要』に倒さない。"
  (:require [clojure.string :as str]
            [okugai.medium :as medium]))

(def regulations
  "規制 id → 根拠。`:trigger` は人が読む発動条件で、機械判定は `applicable`。"
  {:outdoor-ad-permit
   {:reg/id :outdoor-ad-permit
    :reg/name-ja "屋外広告物条例に基づく許可"
    :reg/authority "各都道府県・政令指定都市等"
    :reg/legal-basis "屋外広告物法、および同法に基づく各自治体の屋外広告物条例"
    :reg/evidence-key "outdoor-ad-permit-record"
    :reg/trigger "原則すべての屋外広告物。禁止区域・禁止物件の指定は自治体ごとに異なる。"
    :reg/source-urls ["https://www.rilg.or.jp/htdocs/img/reiki/057_outdoor_advertising.htm"
                      "https://www.toshiseibi.metro.tokyo.lg.jp/documents/d/toshiseibi/pdf_kenchiku_koukoku_pdf_kou_siori"]}

   :road-occupancy
   {:reg/id :road-occupancy
    :reg/name-ja "道路占用許可"
    :reg/authority "道路管理者"
    :reg/legal-basis "道路法第32条"
    :reg/evidence-key "road-occupancy-permit-record"
    :reg/trigger "道路上空・路上に継続して物件を設ける場合。"
    :reg/source-urls ["https://www.city.osaka.lg.jp/kensetsu/page/0000372127.html"]}

   :road-use
   {:reg/id :road-use
    :reg/name-ja "道路使用許可"
    :reg/authority "所轄警察署"
    :reg/legal-basis "道路交通法"
    :reg/evidence-key "road-use-permit-record"
    :reg/trigger "道路に広告板その他これらに類する工作物を設ける場合。"
    :reg/source-urls ["https://www.etic.co.jp/feature/outdoor-advertising-rule/"]}

   :building-code-88
   {:reg/id :building-code-88
    :reg/name-ja "工作物確認申請（広告塔・広告板）"
    :reg/authority "建築主事／指定確認検査機関"
    :reg/legal-basis "建築基準法第88条第1項（施行令第138条第1項第3号）"
    :reg/evidence-key "building-code-88-record"
    :reg/trigger "高さが4mを超える広告塔・広告板。屋上の広告塔も広告塔部分が4m超なら対象。"
    :reg/penalty "確認済証の交付前の着工は違反（1年以下の懲役または100万円以下の罰金）"
    :reg/threshold {:height-m 4.0}
    :reg/source-urls ["https://www.city.ota.tokyo.jp/seikatsu/sumaimachinami/kenchiku/tatemono_tyuuikisei/kousakubutsu.html"
                      "https://www.pref.nagano.lg.jp/toshikei/kurashi/sumai/kokoku/documents/takasanosantei.pdf"]}

   :building-code-64
   {:reg/id :building-code-64
    :reg/name-ja "看板等の防火措置"
    :reg/authority "建築主事／特定行政庁"
    :reg/legal-basis "建築基準法第64条"
    :reg/evidence-key "building-code-64-record"
    :reg/trigger "防火地域内で、建築物の屋上に設けるもの、または高さ3mを超えるもの。主要部分を不燃材料で造るか覆う。"
    :reg/threshold {:height-m 3.0 :zone :fire-prevention-district}
    :reg/source-urls ["https://www.mori-sign.jp/column/sign-building-code-structure"]}

   :expressway
   {:reg/id :expressway
    :reg/name-ja "高速道路の道路区域内／沿道の規制"
    :reg/authority "道路管理者（NEXCO 東日本・中日本・西日本 等）／都道府県"
    :reg/legal-basis "道路法、および都道府県の屋外広告物条例・高速道路等沿道ガイドライン"
    :reg/evidence-key "expressway-authority-record"
    :reg/trigger "道路区域内は道路管理者の許可（媒体としては NEXCO 系の媒体社経由）。沿道は条例の禁止区域指定・ガイドラインが上乗せされうる。"
    :reg/source-urls ["https://www.pref.wakayama.lg.jp/prefg/080900/okugaikoukokubutsujyourei/about_okugaikoukokubutsu_d/fil/kousoku_guideline_1.pdf"
                      "https://www.w-nexco-coms.co.jp/img/common/file/w-nexco-media.pdf"]}})

(def catalog
  "iso3 → 管轄の要件。`:required-evidence` は governor が掲出提案を通す前に実在を
  要求する証跡。"
  {"JPN"
   {:name "Japan"
    :owner-authority "各都道府県・政令指定都市等（屋外広告物条例）／道路管理者（道路占用）／建築主事（工作物確認）"
    :legal-basis "屋外広告物法、道路法、道路交通法、建築基準法"
    :permit-authority-is-municipal? true
    :regulations (vec (sort (keys regulations)))
    ;; 媒体に依らず常に要る証跡。規制由来の証跡は `applicable` の結果から
    ;; 導出する（媒体別に効く規制が違うので、固定リストにすると電柱に
    ;; 工作物確認済証を要求するような嘘になる）。
    :base-evidence ["site-owner-consent-record"
                    "agency-order-record"
                    "creative-spec-record"]
    :prohibited-note "多数の条例が電柱・街路灯柱への貼り紙・貼り札・広告旗を禁止物件として列挙する。これは無断の貼付物に対する規制であり、所有者の許諾と条例許可を経た掲出物とは別扱い。"
    :as-of "2026-08-04"}})

(defn requirements [iso3] (get catalog iso3))
(defn covered? [iso3] (contains? catalog iso3))

(defn coverage []
  {:jurisdictions (vec (sort (keys catalog)))
   :count (count catalog)
   :regulations (count regulations)
   :note (str "収録 " (count catalog) " 管轄 / " (count regulations)
              " 規制。未収録の管轄は spec-basis 無し —— advisor は要件を創作してはならず、"
              "governor は掲出提案を保留する。")})

;; ── 実際に効くかの判定 ──────────────────────────────────────────────

(defn applicable
  "この掲出が実際に受ける規制。`{:medium :billboard :height-m 5.2
  :fire-prevention-district? true :overhangs-road? false :expressway-adjacent? false}`

  戻り値は `{:required #{...} :undetermined #{...} :not-applicable #{...}}`。
  **寸法や区域が不明なら `:undetermined`** —— 不明を『不要』に倒すと、
  確認申請なしで着工する提案が governor を通ってしまう。"
  [{:keys [medium height-m fire-prevention-district? overhangs-road? expressway-adjacent?
           expressway-facility?]}]
  (let [trig (medium/triggers medium)
        req (atom #{}) undet (atom #{}) na (atom #{})
        decide (fn [reg v]
                 (case v
                   :yes (swap! req conj reg)
                   :unknown (swap! undet conj reg)
                   :no (swap! na conj reg)))]
    (when (contains? trig :outdoor-ad-permit)
      ;; 条例許可は原則すべての屋外広告物に効く（禁止区域・適用除外は自治体ごと）
      (decide :outdoor-ad-permit :yes))
    (when (contains? trig :road-occupancy)
      (decide :road-occupancy (cond (nil? overhangs-road?) :unknown
                                    overhangs-road? :yes
                                    :else :no)))
    (when (contains? trig :road-use)
      (decide :road-use (cond (nil? overhangs-road?) :unknown
                              overhangs-road? :yes
                              :else :no)))
    (when (contains? trig :building-code-88)
      (decide :building-code-88 (cond (nil? height-m) :unknown
                                      (> height-m 4.0) :yes
                                      :else :no)))
    (when (contains? trig :building-code-64)
      (decide :building-code-64
              (cond (nil? fire-prevention-district?) :unknown
                    (not fire-prevention-district?) :no
                    ;; 防火地域内: 屋上に設けるもの、または高さ3m超
                    (= :building (:medium/attached-to (medium/describe medium))) :yes
                    (nil? height-m) :unknown
                    (> height-m 3.0) :yes
                    :else :no)))
    (when (contains? trig :expressway)
      (decide :expressway (cond expressway-facility? :yes
                                (nil? expressway-adjacent?) :unknown
                                expressway-adjacent? :yes
                                :else :no)))
    {:required @req :undetermined @undet :not-applicable @na}))

(defn undetermined?
  "判定できない規制が残っているか。残っていたら掲出提案を進めてはいけない。"
  [placement]
  (boolean (seq (:undetermined (applicable placement)))))

(defn required-evidence
  "この掲出に実際に要る証跡キー。基本証跡 + **実際に効く規制**の証跡だけ。
  管轄が未収録なら `:no-spec-basis`。

  `:undetermined` な規制の証跡も要求する —— 要否が決まっていないものを
  『不要だから証跡も不要』にすると、確認申請なしで進む提案が通ってしまう。"
  [iso3 placement]
  (if-let [req (requirements iso3)]
    (let [a (applicable placement)
          regs (into (:required a) (:undetermined a))]
      (vec (distinct (concat (:base-evidence req)
                             (keep #(:reg/evidence-key (get regulations %)) (sort regs))))))
    :no-spec-basis))

(defn missing-evidence
  "掲出提案が持つべき証跡のうち、まだ無いもの。管轄が未収録なら `:no-spec-basis`
  を返す（空 vector ではない —— 空は『全部揃っている』と読めてしまう）。"
  [iso3 placement provided-evidence-keys]
  (let [need (required-evidence iso3 placement)]
    (if (= :no-spec-basis need)
      :no-spec-basis
      (let [have (set (map str provided-evidence-keys))]
        (vec (remove have need))))))

(defn describe-regulation [id] (get regulations id))
