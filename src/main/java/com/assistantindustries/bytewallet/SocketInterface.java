package com.assistantindustries.bytewallet;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.AbstractBlockChainListener;
import org.bitcoinj.core.listeners.BlockChainListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Created by assistant on 27/6/16.
 */
public class SocketInterface {
    private final SocketIOServer server;
    private final Wallet wallet;
    private final WalletAppKit kit;
    private final String username;
    private final String password;

    private HashMap<String, Set<UUID>> addressSubscribers; //Address in Base58
    private HashMap<String, TransactionReceivedListener> listeners;
    private Set<UUID> authorizedSessions;

    public SocketInterface(WalletAppKit kit, SocketIOServer server, Wallet wallet, String username, String password) {
        this.kit = kit;
        this.server = server;
        this.wallet = wallet;
        this.username = username;
        this.password = password;
        this.addressSubscribers = new HashMap<String, Set<UUID>>();
        this.listeners = new HashMap<String, TransactionReceivedListener>();
        this.authorizedSessions = new HashSet<UUID>();
        setListeners();
    }

    public void setListeners() {
        server.addEventListener("generateAddress", Void.class, new DataListener<Void>() {
            public void onData(SocketIOClient client, Void data, AckRequest ackRequest) {
                if (ackRequest.isAckRequested()) {
                    Address c = wallet.freshReceiveAddress();
                    ackRequest.sendAckData(c.toBase58());
                }
            }
        });
        server.addEventListener("getBalance", Void.class, new DataListener<Void>() {
            public void onData(SocketIOClient client, Void data, AckRequest ackRequest) {
                if (ackRequest.isAckRequested() && authorizedSessions.contains(client.getSessionId())) {
                    ackRequest.sendAckData(wallet.getBalance().getValue());
                }
            }
        });
        server.addEventListener("getReceivedForAddress", BalanceForAddress.class, new DataListener<BalanceForAddress>() {
            public void onData(SocketIOClient client, BalanceForAddress data, AckRequest ackRequest) {
                if (ackRequest.isAckRequested()) {
                    Address address = Address.fromBase58(wallet.getParams(), data.getAddress());
                    CoinSelector selector = new AddressReceived(address, data.getMinConfirmations());
                    Coin coins = wallet.getBalance(selector);
                    ackRequest.sendAckData(coins.getValue());
                }
            }
        });

        server.addEventListener("subscribeForAddress", String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String address, AckRequest ackRequest) throws Exception {
                if (authorizedSessions.contains(socketIOClient.getSessionId())) {
                    Address toSubscribe = Address.fromBase58(wallet.getParams(), address);
                    subscribe(toSubscribe, socketIOClient);
                    if (ackRequest.isAckRequested()) {
                        ackRequest.sendAckData(true);
                    }
                }
            }
        });

        server.addEventListener("unsubscribeForAddress", String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String address, AckRequest ackRequest) throws Exception {
                if (authorizedSessions.contains(socketIOClient.getSessionId())) {
                    Address toUnsubscribe = Address.fromBase58(wallet.getParams(), address);
                    unsubscribe(toUnsubscribe, socketIOClient);
                    if (ackRequest.isAckRequested()) {
                        ackRequest.sendAckData(true);
                    }
                }
            }
        });

        server.addEventListener("sendTransaction", SendTransaction.class, new DataListener<SendTransaction>() {
            public void onData(SocketIOClient client, SendTransaction data, AckRequest ackRequest) {
                if(authorizedSessions.contains(client.getSessionId())){
                    boolean achieved = false;
                    String errorDescription = "";
                    try {
                        Address targetAddress = new Address(wallet.getParams(), data.address);
                        // Do the send of BTC in the background. This could throw InsufficientMoneyException.
                        SendRequest request = SendRequest.to(targetAddress, Coin.valueOf(data.amount));
                        System.out.println("Fee per kb: "+request.feePerKb);
                        request.feePerKb = Coin.valueOf(15000L);
                        Address changeAddress = wallet.freshReceiveAddress(); //Generate change address
                        request.changeAddress = changeAddress;
                        Wallet.SendResult result = wallet.sendCoins(kit.peerGroup(),request );
                        // Wait for the transaction to propagate across the P2P network, indicating acceptance.
                        result.broadcastComplete.get();
                        achieved = true;
                    } catch (InsufficientMoneyException e) {
                        errorDescription = "Not enough money";
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }catch (AddressFormatException e) {
                        errorDescription = "Incorrect address format";
                    }
                    if (ackRequest.isAckRequested()) {
                        ackRequest.sendAckData(achieved,errorDescription);
                    }
                }
            }
        });

        server.addEventListener("login", LoginData.class, new DataListener<LoginData>() {
            public void onData(SocketIOClient socketIOClient, LoginData loginData, AckRequest ackRequest) throws Exception {
                boolean loggedIn = false;
                if(loginData.getUser().equals(username)&&loginData.getPass().equals(password)){
                    loggedIn = true;
                    authorizedSessions.add(socketIOClient.getSessionId());
                }
                if(ackRequest.isAckRequested())
                    ackRequest.sendAckData(loggedIn);
            }
        });

        kit.chain().addNewBestBlockListener(new NewBestBlockListener() {
            public void notifyNewBestBlock(StoredBlock storedBlock) throws VerificationException {
                System.out.println("New block detected in network, broadcasting...");
                server.getBroadcastOperations().sendEvent("newBlock");
            }
        });

        server.addDisconnectListener(new DisconnectListener() {
            public void onDisconnect(SocketIOClient socketIOClient) {

                for (String address : addressSubscribers.keySet()) {
                    Set<UUID> clientIds = addressSubscribers.get(address);
                    if (clientIds.contains(socketIOClient.getSessionId())) {
                        unsubscribe(Address.fromBase58(wallet.getParams(), address), socketIOClient);
                    }
                }
            }
        });
    }

    private synchronized void subscribe(final Address address, SocketIOClient client) {
        if (!addressSubscribers.containsKey(address.toBase58()))
            addressSubscribers.put(address.toBase58(), new HashSet<UUID>());
        Set<UUID> subscribers = addressSubscribers.get(address.toBase58());
        if (subscribers.size() == 0) {
            TransactionReceivedListener listener = new TransactionReceivedListener(address.toBase58()) {
                @Override
                public void onTransactionReceived(Wallet wallet, Transaction transaction, Coin prevBalance, Coin newBalance) {
                    Set<UUID> subscribed = addressSubscribers.get(address.toBase58());
                    for (UUID clientId : subscribed) {
                        SocketIOClient client = server.getClient(clientId);
                        client.sendEvent("transactionReceived", address.toBase58());
                    }
                }
            };
            listeners.put(address.toBase58(), listener);
            wallet.addCoinsReceivedEventListener(listener);
            wallet.addWatchedAddress(address);
        }
        subscribers.add(client.getSessionId());
    }

    private synchronized void unsubscribe(Address address, SocketIOClient client) {
        Set<UUID> subscribers = addressSubscribers.get(address.toBase58());
        if (subscribers.size() > 0 && subscribers.contains(client.getSessionId())) {
            if (subscribers.size() == 1) {
                TransactionReceivedListener listener = listeners.get(address.toBase58());
                listeners.remove(address.toBase58());
                wallet.removeCoinsReceivedEventListener(listener);
                wallet.removeWatchedAddress(address);
            }
            subscribers.remove(client.getSessionId());
        }
    }

    public void listenSync() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    private static class BalanceForAddress {
        private String address;
        private int minConfirmations;

        public BalanceForAddress() {
        }

        public BalanceForAddress(String address, int minConfirmations) {
            this.address = address;
            this.minConfirmations = minConfirmations;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getMinConfirmations() {
            return minConfirmations;
        }

        public void setMinConfirmations(int minConfirmations) {
            this.minConfirmations = minConfirmations;
        }
    }

    private static class SendTransaction {
        private long amount; //amount in satoshi
        private String address; //address as base58 String

        public SendTransaction() {
        }

        public SendTransaction(long amount, String address) {
            this.amount = amount;
            this.address = address;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    private static class LoginData {
        private String user;
        private String pass;

        public LoginData(){}

        public LoginData(String user, String pass) {
            this.user = user;
            this.pass = pass;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPass() {
            return pass;
        }

        public void setPass(String pass) {
            this.pass = pass;
        }
    }
}
