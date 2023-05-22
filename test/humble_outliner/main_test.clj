(ns humble-outliner.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [humble-outliner.events :as events]
   [humble-outliner.helpers :refer [to-compact to-compact-by-text from-compact from-compact-by-text update-compact]]))

(deftest event-item-enter-pressed
  (let [entities (from-compact-by-text ["a"
                                        "b" ["ba"
                                             "bb"]])
        db {:entities entities
            :focused-id "b"
            :next-id 1}
        result ((events/item-enter-pressed "b" 0) db)]
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
        (let [result ((events/item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= [1 3] (to-compact (:entities result))))
          (is (= 1 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 1 :text])))))
      (testing "strips trailing spaces when merging item text"
        (let [entities (-> entities (assoc-in [1 :text] "abc   "))
              result ((events/item-beginning-backspace-pressed 2) {:entities entities})]
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
              result ((events/item-beginning-backspace-pressed 13) {:entities entities})]
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
              result ((events/item-beginning-backspace-pressed 2) {:entities entities})]
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
              result ((events/item-beginning-backspace-pressed 11) {:entities entities})]
          (is (= [1 [12]
                  2]
                 (to-compact (:entities result))))
          (is (= 1 (:focused-id result)))
          (is (= "abcdef" (get-in result [:entities 1 :text])))))
      (testing "is first top-level item, noop"
        (is (= entities (:entities ((events/item-beginning-backspace-pressed 1) {:entities entities}))))))
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
            (let [result ((events/item-beginning-backspace-pressed 22) {:entities entities})]
              (is (= [1
                      2 [21 [221
                             222]
                         23]
                      3]
                     (to-compact (:entities result))))
              (is (= 21 (:focused-id result)))
              (is (= "abc2def2" (get-in result [:entities 21 :text])))))
          (testing "top-level"
            (let [result ((events/item-beginning-backspace-pressed 2) {:entities entities})]
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
              result ((events/item-beginning-backspace-pressed 2) {:entities entities})]
          (is (= items (to-compact (:entities result))))))
      (testing "is first child, noop"
        (let [items [1 [11 [111 112]
                        12]]
              entities (-> (from-compact items))
              result ((events/item-beginning-backspace-pressed 11) {:entities entities})]
          (is (= items (to-compact (:entities result))))))
      (testing "is first top-level item, noop"
        (let [items [1 [11
                        12]]
              entities (-> (from-compact items))
              result ((events/item-beginning-backspace-pressed 1) {:entities entities})]
          (is (= items (to-compact (:entities result)))))))))
