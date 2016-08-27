package com.assistantindustries.bytewallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

/**
 * Created by assistant on 27/6/16.
 */
public abstract class TransactionReceivedListener implements WalletCoinsReceivedEventListener{
    private String address; //Address in base58

    public TransactionReceivedListener(String address){
        this.address = address;
    }
    public void onCoinsReceived(Wallet wallet, Transaction transaction, Coin prevBalance, Coin newBalance) {
        for(TransactionOutput output: transaction.getOutputs()){
            String outputAddress = output.getScriptPubKey().getToAddress(wallet.getParams()).toBase58();
            if(outputAddress.equals(address)){
                onTransactionReceived(wallet,transaction,prevBalance,newBalance);
            }
        }
    }

    public abstract void onTransactionReceived(Wallet wallet, Transaction transaction, Coin prevBalance, Coin newBalance);
}
