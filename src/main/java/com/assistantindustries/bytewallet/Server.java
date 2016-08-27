package com.assistantindustries.bytewallet;

import com.corundumstudio.socketio.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by assistant on 12/6/16.
 */
public class Server {
    private WalletAppKit kit;
    private Wallet wallet;

    public void start()  {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            String filename = "config.properties";
            input = Server.class.getClassLoader().getResourceAsStream(filename);
            if(input==null){
                System.out.println("Unable to find configuration file");
                return;
            }
            //load a properties file from class path, inside static method
            prop.load(input);
            Configuration config = new Configuration();
            config.setHostname(prop.getProperty("hostname"));
            config.setPort(Integer.parseInt(prop.getProperty("port")));

            kit = new WalletAppKit( MainNetParams.get(), new File("."), "bytepayr");

            kit.useTor();

            // Download the block chain and wait until it's done.
            kit.startAsync();
            kit.awaitRunning();
            wallet = kit.wallet();

            SocketInterface api = new SocketInterface(kit,new SocketIOServer(config), wallet,prop.getProperty("username"),prop.getProperty("password"));

            System.out.println("Starting server");
            api.listenSync();

            try {
                Thread.sleep(Integer.MAX_VALUE); //Sleep forever
            } catch (InterruptedException e) {
                api.stop();
                kit.stopAsync();
                kit.awaitTerminated();
                System.out.println("Server closed");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally{
            if(input!=null){
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }

    public static void main(String[] args)  {
        Server server = new Server();
        server.start();
    }
}
