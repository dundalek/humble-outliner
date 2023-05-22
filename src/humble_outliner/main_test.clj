(ns humble-outliner.main-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [clojure.walk :as walk]
   [humble-outliner.main :as main]
   [humble-outliner.model :as model]))

(defn to-compact-impl
  [key-fn entities]
  (walk/prewalk
   (fn [x]
     (cond
       (vector? x) (->> x
                        (mapcat (fn [{:keys [children] :as entity}]
                                  (cond-> [(key-fn entity)]
                                    (seq children) (conj children))))
                        (into []))
       :else x))
   (main/stratify entities)))

(defn from-compact-impl
  ([kw items]
   (from-compact-impl kw items nil))
  ([kw items parent-id]
   (loop [entities {}
          i 0
          [id maybe-children & other] items]
     (if id
       (let [entities (assoc entities id (cond-> {:order i}
                                           (not= kw :id) (assoc kw id)
                                           parent-id (assoc :parent parent-id)))]
         (if (vector? maybe-children)
           (recur (merge entities (from-compact-impl kw maybe-children id))
                  (inc i)
                  other)
           (recur entities
                  (inc i)
                  (cons maybe-children other))))
       entities))))

(def to-compact (partial to-compact-impl :id))
(def from-compact (partial from-compact-impl :id))
(def to-compact-by-text (partial to-compact-impl :text))
(def from-compact-by-text (partial from-compact-impl :text))

(deftest to-compact-test
  (is (= [4 5 6]
         (to-compact {6 {:order 2}
                      5 {:order 1}
                      4 {:order 0}})))
  (is (= [1
          2 [21
             22 [221]]
          3]
         (to-compact {1 {:order 0}
                      2 {:order 1}
                      21 {:order 0 :parent 2}
                      22 {:order 1 :parent 2}
                      221 {:order 0 :parent 22}
                      3 {:order 2}})))
  (is (= ["four" "five" "six"]
         (to-compact-by-text {6 {:order 2 :text "six"}
                              5 {:order 1 :text "five"}
                              4 {:order 0 :text "four"}}))))

(deftest from-compact-test
  (is (= {6 {:order 2}
          5 {:order 1}
          4 {:order 0}}
         (from-compact [4 5 6])))

  (is (= {1 {:order 0}
          2 {:order 1}
          21 {:order 0 :parent 2}
          22 {:order 1 :parent 2}
          221 {:order 0 :parent 22}
          3 {:order 2}}
         (from-compact [1
                        2 [21
                           22 [221]]
                        3])))
  (is (= {"four" {:order 0 :text "four"}
          "five" {:order 1 :text "five"}
          "six" {:order 2 :text "six"}}
         (from-compact-by-text ["four" "five" "six"]))))

(defn update-compact [compact f & args]
  (to-compact (apply f (from-compact compact) args)))

(deftest update-compact-test
  (is (= [1 2]
         (update-compact [1 2 3] #(dissoc % 3)))))

(deftest compact-round-trip
  (are [compact] (= compact (update-compact compact identity))
    []

    [1 2 3]

    [1
     2 [21
        22 [221]]
     3])

  (are [items] (= items (to-compact-by-text (from-compact-by-text items)))
    []

    ["four" "five" "six"]

    ["1"
     "2" ["21"
          "22" ["221"]]
     "3"]))

(deftest index-of
  (is (= 1 (main/index-of [4 5 6] 5)))
  (is (nil? (main/index-of [4 5 6] 42))))

(deftest remove-at
  (is (= [5 6] (main/remove-at [4 5 6] 0)))
  (is (= [4 6] (main/remove-at [4 5 6] 1)))
  (is (= [4 5] (main/remove-at [4 5 6] 2)))
  (is (thrown? java.lang.IndexOutOfBoundsException (main/remove-at [4 5 6] 3))))

(deftest insert-after
  (is (= [4 42 5 6] (main/insert-after [4 5 6] 4 42)))
  (is (= [4 5 6 42] (main/insert-after [4 5 6] 6 42)))
  (testing "if no item then append"
    (is (= [4 5 6 42] (main/insert-after [4 5 6] 7 42)))))

(deftest insert-before
  (is (= [42 4 5 6] (main/insert-before [4 5 6] 4 42)))
  (is (= [4 5 42 6] (main/insert-before [4 5 6] 6 42)))
  (testing "if no item then prepend"
    (is (= [42 4 5 6] (main/insert-before [4 5 6] 7 42)))))

(deftest recalculate-entities-order
  (let [entities {6 {:order 2}
                  5 {:order 1}
                  4 {:order 0}}
        entities' (model/recalculate-entities-order entities [6 5 4])]
    (is (= entities' {6 {:order 0}
                      5 {:order 1}
                      4 {:order 2}}))

    (is (= [4 5 6] (main/get-children-order entities nil)))
    (is (= [6 5 4] (main/get-children-order entities' nil)))))

(deftest get-children-order
  (let [entities {7 {:order 1 :parent 4}
                  6 {:order 1}
                  5 {:order 0 :parent 4}
                  4 {:order 0}}]
    (is (= [4 6] (main/get-children-order entities nil)))
    (is (= [5 7] (main/get-children-order entities 4)))))

(deftest item-indent
  (let [entities
        {6 {:order 2}
         5 {:order 1}
         4 {:order 0}}]

    (is (= {6 {:order 2}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (main/item-indent entities 5)))

    (is (= {6 {:order 1 :parent 4}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (-> entities
               (main/item-indent 5)
               (main/item-indent 6))))

    (is (= {6 {:order 0 :parent 5}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (-> entities
               (main/item-indent 5)
               (main/item-indent 6)
               (main/item-indent 6))))

    (testing "noop indenting first item"
      (is (= entities (main/item-indent entities 4))))

    (testing "noop indenting first sub-item"
      (is (= (main/item-indent entities 5)
             (-> entities
                 (main/item-indent 5)
                 (main/item-indent 5)))))))

(deftest item-outdent
  (let [items [1
               2 [21
                  22 [221]]
               3]]
    (testing "noop outdenting top-level item"
      (is (= items (update-compact items main/item-outdent 2))))

    (testing "straightforward outdenting last item"
      (is (= [1
              2 [21
                 22
                 221]
              3]
             (update-compact items main/item-outdent 221))))

    (testing "outdenting reparents siblings"
      (is (= [1
              2
              21 [22 [221]]
              3]
             (update-compact items main/item-outdent 21))))))

(deftest last-child
  (let [entities {6 {:order 2}
                  5 {:order 0 :parent 4}
                  4 {:order 0}}]
    (is (= 6 (main/last-child entities nil)))
    (is (= 5 (main/last-child entities 4)))
    (is (= nil (main/last-child entities 6)))))

(deftest find-item-up
  (let [entities (from-compact
                  [1
                   2 [21
                      22 [221]]
                   3])]
    (testing "focus previous top-level"
      (is (= 1 (main/find-item-up entities 2))))

    (testing "focus previous nested"
      (is (= 21 (main/find-item-up entities 22))))

    (testing "focus parent of nested first item"
      (is (= 2 (main/find-item-up entities 21))))

    (testing "if previous item has children it focuses last child"
      (is (= 221 (main/find-item-up entities 3))))

    (testing "no item before first top-level item"
      (is (= nil (main/find-item-up entities 1))))))

(deftest find-item-down
  (let [entities (from-compact
                  [1
                   2 [21
                      22 [221]]
                   3])]
    (testing "focus next top-level"
      (is (= 2 (main/find-item-down entities 1))))

    (testing "focus first nested child"
      (is (= 21 (main/find-item-down entities 2))))

    (testing "focus next nested item"
      (is (= 22 (main/find-item-down entities 21))))

    (testing "focus next sibling for last child"
      (is (= 3 (main/find-item-down entities 221))))

    (testing "no next item for last top-level item"
      (is (= nil (main/find-item-down entities 3))))))

(deftest item-add
  (let [items [1
               2 [21]]]
    (testing "adding to childless item adds as sibling"
      (is (= [1
              7
              2 [21]]
             (update-compact items main/item-add 1 7)))
      (is (= [1
              2 [21
                 7]]
             (update-compact items main/item-add 21 7))))

    (testing "adding to item with children adds as a first child"
      (is (= [1
              2
              [7
               21]]
             (update-compact items main/item-add 2 7))))))

(deftest event-item-enter-pressed
  (let [entities (from-compact-by-text ["a"
                                        "b" ["ba"
                                             "bb"]])
        db {:entities entities
            :focused-id "b"
            :next-id 1}
        result ((main/event-item-enter-pressed "b" 0) db)]
    (is (= ["a"
            ""
            "b" ["ba"
                 "bb"]]
           (to-compact-by-text (:entities result))))
    (is (= "b" (get-in result [:entities (:focused-id result) :text])))))

(deftest item-backspace
  (let [items [1 2 3]
        entities (-> (from-compact items)
                     (assoc-in [1 :text] "abc")
                     (assoc-in [2 :text] "def"))]
    (testing "without children"
      (testing "top-level has sibling before without children, merge into it"
        (let [result ((main/event-item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= [1 3] (to-compact (:entities result))))
          (is (= 1 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 1 :text])))))
      (testing "strips trailing spaces when merging item text"
        (let [entities (-> entities (assoc-in [1 :text] "abc   "))
              result ((main/event-item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= [1 3] (to-compact (:entities result))))
          (is (= 1 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 1 :text])))))
      (testing "nested has sibling before without children, merge into it"
        (let [items [1 [11
                        12 [121
                            122]
                        13]
                     2]
              entities (-> (from-compact items)
                           (assoc-in [122 :text] "abc")
                           (assoc-in [13 :text] "def"))
              result ((main/event-item-beginning-backspace-pressed 13) {:entities entities})]
          (is (= [1 [11
                     12 [121
                         122]]
                  2]
                 (to-compact (:entities result))))
          (is (= 122 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 122 :text])))))
      (testing "has sibling before with children, merge into last descendent (like focus up)"
        (let [items [1 [11
                        12 [121
                            122]]
                     2
                     3]
              entities (-> (from-compact items)
                           (assoc-in [122 :text] "abc")
                           (assoc-in [2 :text] "def"))
              result ((main/event-item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= [1 [11
                     12 [121
                         122]]
                  3]
                 (to-compact (:entities result))))
          (is (= 122 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 122 :text])))))
      (testing "is first child, merge into parent"
        (let [items [1 [11
                        12]
                     2]
              entities (-> (from-compact items)
                           (assoc-in [1 :text] "abc")
                           (assoc-in [11 :text] "def"))
              result ((main/event-item-beginning-backspace-pressed 11) {:entities entities})]
          (is (= [1 [12]
                  2]
                 (to-compact (:entities result))))
          (is (= 1 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 1 :text])))))
      (testing "is first top-level item, noop"
        (is (= entities (:entities ((main/event-item-beginning-backspace-pressed 1) {:entities entities}))))))
    (testing "with children"
      (testing "has sibling before without children, merge into it and reparent children"
        (let [items [1
                     2 [21
                        22 [221
                            222]
                        23]
                     3]
              entities (-> (from-compact items)
                           (assoc-in [1 :text] "abc1")
                           (assoc-in [2 :text] "def1")
                           (assoc-in [21 :text] "abc2")
                           (assoc-in [22 :text] "def2"))]
          (testing "nested"
            (let [result ((main/event-item-beginning-backspace-pressed 22) {:entities entities})]
              (is (= [1
                      2 [21 [221
                             222]
                         23]
                      3]
                     (to-compact (:entities result))))
              (is (= 21 (:focused-id result)))
              (is (= "abc2def2" (get-in result [:entities 21 :text])))))
          (testing "top-level"
            (let [result ((main/event-item-beginning-backspace-pressed 2) {:entities entities})]
              (is (= [1 [21
                         22 [221
                             222]
                         23]
                      3]
                     (to-compact (:entities result))))
              (is (= 1 (:focused-id result)))
              (is (= "abc1def1" (get-in result [:entities 1 :text])))))))
      (testing "has sibling before with children, noop"
        (let [items [1 [11
                        12]
                     2 [21]]
              entities (-> (from-compact items))
              result ((main/event-item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= items (to-compact (:entities result))))))
      (testing "is first child, noop"
        (let [items [1 [11 [111 112]
                        12]]
              entities (-> (from-compact items))
              result ((main/event-item-beginning-backspace-pressed 11) {:entities entities})]
          (is (= items (to-compact (:entities result))))))
      (testing "is first top-level item, noop"
        (let [items [1 [11
                        12]]
              entities (-> (from-compact items))
              result ((main/event-item-beginning-backspace-pressed 1) {:entities entities})]
          (is (= items (to-compact (:entities result)))))))))

(deftest item-move-up
  (let [items [1 [11
                  12]
               2 [21
                  22 [223]]]]
    (testing "moving top level item"
      (is (= [2 [21
                 22 [223]]
              1 [11
                 12]]
             (update-compact items main/item-move-up 2))))
    (testing "moving nested item within same parent"
      (is (= [1 [11
                 12]
              2 [22 [223]
                 21]]
             (update-compact items main/item-move-up 22))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (is (= [1 [11
                 12
                 21]
              2 [22 [223]]]
             (update-compact items main/item-move-up 21))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (let [expected-items [1 [11
                               12]
                            2 [21 [223]
                               22]]]
        (is (= expected-items (update-compact items main/item-move-up 223)))
        (testing "moving nested item that can't keep indent level does nothing"
          (is (= expected-items (-> items
                                    (update-compact main/item-move-up 223)
                                    (update-compact main/item-move-up 223)))))))
    (testing "moving first top level item up does nothing"
      (is (= items (update-compact items main/item-move-up 1))))
    (testing "moving nested item that can't keep indent level does nothing"
      (is (= items (update-compact items main/item-move-up 11))))))

(deftest item-move-down
  (let [items [1 [11
                  12]
               2 [21
                  22 [223]]]]
    (testing "moving top level item"
      (is (= [2 [21
                 22 [223]]
              1 [11
                 12]]
             (update-compact items main/item-move-down 1))))
    (testing "moving nested item within same parent"
      (is (= [1 [11
                 12]
              2 [22 [223]
                 21]]
             (update-compact items main/item-move-down 21))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (is (= [1 [11]
              2 [12
                 21
                 22 [223]]]
             (update-compact items main/item-move-down 12))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (let [items [1 [11
                      12]
                   2 [21 [223]
                      22]]
            expected-items [1 [11
                               12]
                            2 [21
                               22 [223]]]]
        (is (= expected-items (update-compact items main/item-move-down 223)))
        (testing "moving nested item that can't keep indent level does nothing"
          (is (= expected-items (-> items
                                    (update-compact main/item-move-down 223)
                                    (update-compact main/item-move-down 223)))))))
    (testing "moving last top level item up does nothing"
      (is (= items (update-compact items main/item-move-down 2))))
    (testing "moving nested item that can't keep indent level does nothing"
      (is (= items (update-compact items main/item-move-down 22)))
      (is (= items (update-compact items main/item-move-down 223))))))
