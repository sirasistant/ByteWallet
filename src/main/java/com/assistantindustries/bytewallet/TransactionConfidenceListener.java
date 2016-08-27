package com.assistantindustries.bytewallet;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;

/**
 * Created by assistant on 29/6/16.
 */
public abstract class TransactionConfidenceListener implements TransactionConfidenceEventListener {

    private final Transaction transaction;

    public TransactionConfidenceListener(Transaction transaction){
        this.transaction = transaction;
    }

    public void onTransactionConfidenceChanged(Wallet wallet, Transaction transaction) {
        if(transaction.equals(this.transaction)){
            onConfidenceChanged(wallet,transaction);
        }
    }

    public abstract void onConfidenceChanged(Wallet wallet, Transaction transaction);
}
