(ns humble-outliner.model-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [humble-outliner.helpers :refer [from-compact update-compact]]
   [humble-outliner.model :as model]))

(deftest index-of
  (is (= 1 (model/index-of [4 5 6] 5)))
  (is (nil? (model/index-of [4 5 6] 42))))

(deftest remove-at
  (is (= [5 6] (model/remove-at [4 5 6] 0)))
  (is (= [4 6] (model/remove-at [4 5 6] 1)))
  (is (= [4 5] (model/remove-at [4 5 6] 2)))
  (is (thrown? java.lang.IndexOutOfBoundsException (model/remove-at [4 5 6] 3))))

(deftest insert-after
  (is (= [4 42 5 6] (model/insert-after [4 5 6] 4 42)))
  (is (= [4 5 6 42] (model/insert-after [4 5 6] 6 42)))
  (testing "if no item then append"
    (is (= [4 5 6 42] (model/insert-after [4 5 6] 7 42)))))

(deftest insert-before
  (is (= [42 4 5 6] (model/insert-before [4 5 6] 4 42)))
  (is (= [4 5 42 6] (model/insert-before [4 5 6] 6 42)))
  (testing "if no item then prepend"
    (is (= [42 4 5 6] (model/insert-before [4 5 6] 7 42)))))

(deftest recalculate-entities-order
  (let [entities {6 {:order 2}
                  5 {:order 1}
                  4 {:order 0}}
        entities' (model/recalculate-entities-order entities [6 5 4])]
    (is (= entities' {6 {:order 0}
                      5 {:order 1}
                      4 {:order 2}}))

    (is (= [4 5 6] (model/get-children-order entities nil)))
    (is (= [6 5 4] (model/get-children-order entities' nil)))))

(deftest get-children-order
  (let [entities {7 {:order 1 :parent 4}
                  6 {:order 1}
                  5 {:order 0 :parent 4}
                  4 {:order 0}}]
    (is (= [4 6] (model/get-children-order entities nil)))
    (is (= [5 7] (model/get-children-order entities 4)))))

(deftest item-indent
  (let [entities
        {6 {:order 2}
         5 {:order 1}
         4 {:order 0}}]

    (is (= {6 {:order 2}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (model/item-indent entities 5)))

    (is (= {6 {:order 1 :parent 4}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (-> entities
               (model/item-indent 5)
               (model/item-indent 6))))

    (is (= {6 {:order 0 :parent 5}
            5 {:order 0 :parent 4}
            4 {:order 0}}
           (-> entities
               (model/item-indent 5)
               (model/item-indent 6)
               (model/item-indent 6))))

    (testing "noop indenting first item"
      (is (= entities (model/item-indent entities 4))))

    (testing "noop indenting first sub-item"
      (is (= (model/item-indent entities 5)
             (-> entities
                 (model/item-indent 5)
                 (model/item-indent 5)))))))

(deftest item-outdent
  (let [items [1
               2 [21
                  22 [221]]
               3]]
    (testing "noop outdenting top-level item"
      (is (= items (update-compact items model/item-outdent 2))))

    (testing "straightforward outdenting last item"
      (is (= [1
              2 [21
                 22
                 221]
              3]
             (update-compact items model/item-outdent 221))))

    (testing "outdenting reparents siblings"
      (is (= [1
              2
              21 [22 [221]]
              3]
             (update-compact items model/item-outdent 21))))))

(deftest last-child
  (let [entities {6 {:order 2}
                  5 {:order 0 :parent 4}
                  4 {:order 0}}]
    (is (= 6 (model/last-child entities nil)))
    (is (= 5 (model/last-child entities 4)))
    (is (= nil (model/last-child entities 6)))))

(deftest find-item-up
  (let [entities (from-compact
                  [1
                   2 [21
                      22 [221]]
                   3])]
    (testing "focus previous top-level"
      (is (= 1 (model/find-item-up entities 2))))

    (testing "focus previous nested"
      (is (= 21 (model/find-item-up entities 22))))

    (testing "focus parent of nested first item"
      (is (= 2 (model/find-item-up entities 21))))

    (testing "if previous item has children it focuses last child"
      (is (= 221 (model/find-item-up entities 3))))

    (testing "no item before first top-level item"
      (is (= nil (model/find-item-up entities 1))))))

(deftest find-item-down
  (let [entities (from-compact
                  [1
                   2 [21
                      22 [221]]
                   3])]
    (testing "focus next top-level"
      (is (= 2 (model/find-item-down entities 1))))

    (testing "focus first nested child"
      (is (= 21 (model/find-item-down entities 2))))

    (testing "focus next nested item"
      (is (= 22 (model/find-item-down entities 21))))

    (testing "focus next sibling for last child"
      (is (= 3 (model/find-item-down entities 221))))

    (testing "no next item for last top-level item"
      (is (= nil (model/find-item-down entities 3))))))

(deftest item-add
  (let [items [1
               2 [21]]]
    (testing "adding to childless item adds as sibling"
      (is (= [1
              7
              2 [21]]
             (update-compact items model/item-add 1 7)))
      (is (= [1
              2 [21
                 7]]
             (update-compact items model/item-add 21 7))))

    (testing "adding to item with children adds as a first child"
      (is (= [1
              2
              [7
               21]]
             (update-compact items model/item-add 2 7))))))

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
             (update-compact items model/item-move-up 2))))
    (testing "moving nested item within same parent"
      (is (= [1 [11
                 12]
              2 [22 [223]
                 21]]
             (update-compact items model/item-move-up 22))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (is (= [1 [11
                 12
                 21]
              2 [22 [223]]]
             (update-compact items model/item-move-up 21))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (let [expected-items [1 [11
                               12]
                            2 [21 [223]
                               22]]]
        (is (= expected-items (update-compact items model/item-move-up 223)))
        (testing "moving nested item that can't keep indent level does nothing"
          (is (= expected-items (-> items
                                    (update-compact model/item-move-up 223)
                                    (update-compact model/item-move-up 223)))))))
    (testing "moving first top level item up does nothing"
      (is (= items (update-compact items model/item-move-up 1))))
    (testing "moving nested item that can't keep indent level does nothing"
      (is (= items (update-compact items model/item-move-up 11))))))

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
             (update-compact items model/item-move-down 1))))
    (testing "moving nested item within same parent"
      (is (= [1 [11
                 12]
              2 [22 [223]
                 21]]
             (update-compact items model/item-move-down 21))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (is (= [1 [11]
              2 [12
                 21
                 22 [223]]]
             (update-compact items model/item-move-down 12))))
    (testing "moving nested item that keeps indentation level but changes parent"
      (let [items [1 [11
                      12]
                   2 [21 [223]
                      22]]
            expected-items [1 [11
                               12]
                            2 [21
                               22 [223]]]]
        (is (= expected-items (update-compact items model/item-move-down 223)))
        (testing "moving nested item that can't keep indent level does nothing"
          (is (= expected-items (-> items
                                    (update-compact model/item-move-down 223)
                                    (update-compact model/item-move-down 223)))))))
    (testing "moving last top level item up does nothing"
      (is (= items (update-compact items model/item-move-down 2))))
    (testing "moving nested item that can't keep indent level does nothing"
      (is (= items (update-compact items model/item-move-down 22)))
      (is (= items (update-compact items model/item-move-down 223))))))
