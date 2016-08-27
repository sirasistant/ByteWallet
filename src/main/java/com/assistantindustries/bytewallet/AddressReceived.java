package com.assistantindustries.bytewallet;

import org.bitcoinj.core.*;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.math.BigInteger;
import java.util.*;

/**
 * This class implements a {@link org.bitcoinj.wallet.CoinSelector} which attempts to select all outputs
 * from a designated address. Outputs are selected in order of highest priority.  Note that this means we may
 * end up "spending" more priority than would be required to get the transaction we are creating confirmed.
 */

public class AddressReceived implements CoinSelector {

    private Address addressToQuery;
    private int minDepth;

    public AddressReceived(Address addressToQuery, int minDepth) {
        this.addressToQuery = addressToQuery;
        this.minDepth = minDepth;
    }

    public CoinSelection select(Coin biTarget, List<TransactionOutput> candidates) {
        long target = biTarget.longValue();
        HashSet<TransactionOutput> selected = new HashSet<TransactionOutput>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<TransactionOutput>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        if (!biTarget.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long totalOutputValue = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (totalOutputValue >= target) break;
            // Only pick chain-included transactions, or transactions that are ours and pending.
            if (!shouldSelect(output)) continue;
            selected.add(output);
            totalOutputValue += output.getValue().longValue();
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(totalOutputValue), selected);
    }

    static void sortOutputs(ArrayList<TransactionOutput> outputs) {
        Collections.sort(outputs, new Comparator<TransactionOutput>() {
            public int compare(TransactionOutput a, TransactionOutput b) {
                int depth1 = 0;
                int depth2 = 0;
                TransactionConfidence conf1 = a.getParentTransaction().getConfidence();
                TransactionConfidence conf2 = b.getParentTransaction().getConfidence();
                if (conf1.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                    depth1 = conf1.getDepthInBlocks();
                if (conf2.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                    depth2 = conf2.getDepthInBlocks();
                Coin aValue = a.getValue();
                Coin bValue = b.getValue();
                BigInteger aCoinDepth = BigInteger.valueOf(aValue.value).multiply(BigInteger.valueOf(depth1));
                BigInteger bCoinDepth = BigInteger.valueOf(bValue.value).multiply(BigInteger.valueOf(depth2));
                int c1 = bCoinDepth.compareTo(aCoinDepth);
                if (c1 != 0) return c1;
                // The "coin*days" destroyed are equal, sort by value alone to get the lowest transaction size.
                int c2 = bValue.compareTo(aValue);
                if (c2 != 0) return c2;
                // They are entirely equivalent (possibly pending) so sort by hash to ensure a total ordering.
                BigInteger aHash = a.getParentTransaction().getHash().toBigInteger();
                BigInteger bHash = b.getParentTransaction().getHash().toBigInteger();
                return aHash.compareTo(bHash);
            }
        });
    }

    /** Sub-classes can override this to just customize whether transactions are usable, but keep age sorting. */
    protected boolean shouldSelect(TransactionOutput output) {
        Address outputToAddress = output.getScriptPubKey().getToAddress(addressToQuery.getParameters());
        try {
            // Check if output address matches addressToQuery and check if it can be spent.
            if(outputToAddress.equals(addressToQuery)) {
                return isSelectable(output.getParentTransaction());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean isSelectable(Transaction tx) {
        // Only pick chain-included transactions, or transactions that are ours and pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return (type.equals(TransactionConfidence.ConfidenceType.BUILDING) && confidence.getDepthInBlocks() >= minDepth )
                || (type.equals(TransactionConfidence.ConfidenceType.PENDING)
                && ((confidence.getSource().equals(TransactionConfidence.Source.SELF) && confidence.numBroadcastPeers() > 1 )
                || minDepth==0 ));
    }
}