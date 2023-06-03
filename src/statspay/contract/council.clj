(ns statspay.contract.council
  (:require [std.lang :as l]
            [std.lib :as h]
	    [rt.solidity :as s]))

(l/script :solidity
  {:require [[rt.solidity :as s]]})

(definterface.sol IERC20
  [^{:- [:external]
     :static/returns :bool}
   transfer [:address to
             :uint value]
   ^{:- [:external :view]
     :static/returns :uint}
   balanceOf [:address owner]])

(defenum.sol VoteType
  [:Accept :Reject :Abstain])

(defenum.sol ProposalType
  [:MemberTakeProfit :MemberAdd :MemberRemove])

(defenum.sol ProposalStatus
  [:Undefined :Pending :Cancelled :Accepted :Rejected])

(defstruct.sol Proposal
  [:% -/ProposalStatus status]
  [:% -/ProposalType   proposal-type]
  [:address    value]
  [:uint8      total-yes]
  [:uint8      total-no]
  [:uint8      total-abstained]
  [:uint8      total-voting])

(def.sol ^{:- [:uint8 :public]}
  g:MemberQuorum)

(def.sol ^{:- [:address [] :public]}
  g:MemberArray)

(defmapping.sol ^{:- [:public]}
  g:ProposalActive
  [:address :string])

(defmapping.sol ^{:- [:public]}
  g:ProposalLookup
  [:string -/Proposal])

(defmapping.sol ^{:- [:public]}
  g:ProposalVoting
  [:string (:mapping [:address :bool])])

(defevent.sol CouncilEvent
  [:string     event-type]
  [:string     event-id]
  [:address    event-address]
  [:uint       event-value])

;;
;; CONTRUCTOR
;;

(defconstructor.sol
  __init__
  [:uint8 quorum]
  (. -/g:MemberArray
     (push s/msg-sender))
  (:= -/g:MemberQuorum quorum))

(defn.sol ^{:- [:internal :view]
            :static/returns :bool}
  ut:get-quorum
  "gets the required number of votes for a verdict"
  {:added "0.1"}
  [:string :memory proposal-id]
  (var (-/Proposal :memory proposal)
       (. -/g:ProposalLookup [proposal-id]))
  (when (== (. proposal total-voting)
            (. proposal total-abstained))
    (return false))
  
  (var (:uint total) (- (. proposal total-voting)
                        (. proposal total-abstained)))
  (cond (> (/ (* (. proposal total-yes) 100)
              total)
           -/g:MemberQuorum)
        (return true)
        
        (> (/ (* (. proposal total-no) 100)
              total)
           -/g:MemberQuorum)
        (return false)

        :else
        (s/revert "No Quorum.")))

(defn.sol ^{:- [:internal :view]
            :static/returns :uint}
  ut:get-index
  "gets the current array index"
  {:added "0.1"}
  [:address user-address]
  (when (== (. -/g:MemberArray length) 0)
    (return 0))
  (for [[(:uint i) := 0]
        [(< (uint i) (. -/g:MemberArray length))]
        [(:= i (+ i 1))]]
    (if (==  (. -/g:MemberArray [i])
             user-address)
      (return  (+ i 1))))
  (return 0))

(defn.sol ^{:- [:internal]
            :static/returns :bool}
  ut:get-remove
  "removes from array"
  {:added "0.1"}
  [:address user-address]
  (var (:uint i) (-/ut:get-index user-address))
  (when (== i 0)
    (return false))
  
  (var (:uint last-idx) (- (. -/g:MemberArray length)
                           1))
  (== (. -/g:MemberArray [(- i 1)])
      (. -/g:MemberArray [last-idx]))
  (. -/g:MemberArray (pop))
  (return true))

(defn.sol ^{:- [:internal]
            :static/returns :bool}
  ut:get-add
  "adds to array"
  {:added "0.1"}
  [:address user-address]
  (var (:uint i) (-/ut:get-index user-address))
  (cond (== i 0)
        (do  (. -/g:MemberArray
                (push user-address))
             
             (return true))

        :else
        (return false)))

(defn.sol ^{:- [:internal]}
  action:member-take-profit
  "takes profit for erc20 tokens"
  {:added "0.1"}
  [:string :memory proposal-id :address erc20-address]
  (var (-/IERC20 erc20) (-/IERC20 erc20-address))
  (var (:uint total) (. erc20 (balanceOf (address this))))
  (var (:uint part)  (/ total (. -/g:MemberArray length)))
  (for [[(:uint i) := 0]
        [(< i (. -/g:MemberArray length))]
        [(:= i (+ i 1))]]
    (. erc20 (transfer (. -/g:MemberArray [i])
                       part)))
  (emit (-/CouncilEvent "member_take_profit"
                         proposal-id
                         erc20-address
                         total)))

(defn.sol ^{:- [:internal]}
  action:member-add
  "adds a member to the council"
  {:added "0.1"}
  [:string :memory proposal-id :address user-address]
  (when (-/ut:get-add  user-address)
    (emit (-/CouncilEvent "member_add"
                         proposal-id
                         user-address
                         (. -/g:MemberArray length)))))

(defn.sol ^{:- [:internal]}
  action:member-remove
  "removes a member to the council"
  {:added "0.1"}
  [:string :memory proposal-id :address user-address]
  (when (-/ut:get-remove  user-address)
    (emit (-/CouncilEvent "member_remove"
                         proposal-id
                         user-address
                         (. -/g:MemberArray length)))))

(defn.sol ^{:- [:external]}
  member:proposal-create
  "creates a proposal"
  {:added "0.1"}
  [:string :memory proposal-id
   :% -/ProposalType proposal-type
   :address value]
  (s/require (< 0 (-/ut:get-index s/msg-sender))
             "Member only.")
  
  (s/require (not= (. -/ProposalStatus Pending)
                   (. -/g:ProposalLookup
                      [(. -/g:ProposalActive [s/msg-sender])]
                      status))
             "Proposal pending.")
  (s/require (== (. -/ProposalStatus Undefined)
                 (. -/g:ProposalLookup [proposal-id] status))
             "Proposal exists.")
  (var (-/Proposal :memory proposal)
       (-/Proposal
        {:status (. -/ProposalStatus Pending)
         :proposal-type  proposal-type
         :value value
         :total-yes 1
         :total-no  0
         :total-abstained 0
         :total-voting (uint8 (. -/g:MemberArray length))}))
  (:= (. -/g:ProposalLookup [proposal-id])
      proposal)
  (:= (. -/g:ProposalActive [s/msg-sender])
      proposal-id)
  (:= (. -/g:ProposalVoting [proposal-id] [s/msg-sender])
      true))

(defn.sol ^{:- [:external]}
  member:proposal-vote
  "votes for a proposal"
  {:added "0.1"}
  [:string :memory proposal-id
   :% -/VoteType vote-type]
  (s/require (< 0 (-/ut:get-index s/msg-sender))
             "Member only.")
  (s/require (== (. -/ProposalStatus Pending)
                 (. -/g:ProposalLookup [proposal-id] status))
             "Invalid status.")
  (s/require (not (. -/g:ProposalVoting [proposal-id] [s/msg-sender])))
  (cond (== vote-type (. -/VoteType Accept))
        (:++ (. -/g:ProposalLookup [proposal-id] total-yes))

        (== vote-type (. -/VoteType Reject))
        (:++ (. -/g:ProposalLookup [proposal-id] total-no))

        (== vote-type (. -/VoteType Abstain))
        (:++ (. -/g:ProposalLookup [proposal-id] total-abstained)))
  (:= (. -/g:ProposalVoting [proposal-id] [s/msg-sender])
      true))

(defn.sol ^{:- [:external]}
  member:proposal-cancel
  "cancels a proposal"
  {:added "0.1"}
  [:string :memory proposal-id]
  (s/require (== (. -/ProposalStatus Pending)
                 (. -/g:ProposalLookup
                    [(. -/g:ProposalActive [s/msg-sender])]
                    status))
             "Not pending.")
  (s/require (== (. -/ProposalStatus Pending)
                 (. -/g:ProposalLookup [proposal-id] status))
             "Invalid status.")
  (:= (. -/g:ProposalLookup [proposal-id] status)
      (. -/ProposalStatus Cancelled))
  (delete (. -/g:ProposalActive [s/msg-sender])))

(defn.sol ^{:- [:external]}
  member:proposal-process
  "processes the proposal is quorum is reached"
  {:added "0.1"}
  [:string :memory proposal-id]
  (s/require (== (. -/ProposalStatus Pending)
                 (. -/g:ProposalLookup
                      [(. -/g:ProposalActive [s/msg-sender])]
                      status))
             "Not pending.")
  (s/require (== (. -/ProposalStatus Pending)
                 (. -/g:ProposalLookup [proposal-id] status))
             "Invalid status.")
  (var (:bool result) (-/ut:get-quorum proposal-id))
  (:= (. -/g:ProposalLookup [proposal-id] status)
      (:? result
          (. -/ProposalStatus Accepted)
          (. -/ProposalStatus Rejected)))
  (delete (. -/g:ProposalActive [s/msg-sender]))

  (when result
    (var (-/ProposalType proposal-type)    (. -/g:ProposalLookup [proposal-id] proposal-type))
    (var (:address event-address) (. -/g:ProposalLookup [proposal-id] value))
    (cond (== proposal-type (. -/ProposalType MemberAdd))
          (-/action:member-add proposal-id event-address)
          
          (== proposal-type (. -/ProposalType MemberRemove))
          (-/action:member-remove proposal-id event-address)

          (== proposal-type (. -/ProposalType MemberTakeProfit))
          (-/action:member-take-profit proposal-id event-address))))

(defonce +default-contract-sym+
  (h/ns-sym))

(def +default-contract+
  {:ns   +default-contract-sym+  
   :name "StatstradeCouncil"
   :args [60]})
