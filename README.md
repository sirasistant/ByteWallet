# Getting Started

How to start a ByteWallet server:

Download or clone this repository, and navigate to the container folder. 
Create the file src/main/resources/config.properties , with the following structure:
```
hostname=localhost
port=THE_PORT_WHERE_THE_SERVER_SHOULD_LISTEN
username=THE_WALLET_ACCESS_USERNAME
password=THE_WALLET_ACCESS_PASSWORD
```
After that, compile the wallet with maven:

`mvn install`

When it has finished, start the wallet issuing the command: 

`java -jar target/bytewallet-VERSION_NAME.jar`

And you are done!



# License

The MIT License (MIT)
Copyright (c) 2016 Álvaro Rodríguez Villalba https://github.com/sirasistant

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
