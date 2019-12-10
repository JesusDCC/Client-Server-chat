import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class Server
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );
  static ArrayList<Client> clientList = new ArrayList<>();
  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();



      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            OutputStream ops = s.getOutputStream();

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            Client newClient = new Client(sc);
            clientList.add(newClient);
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  Client left = getClient(sc);
                  String msg = "LEFT " + left.nick;
                  msgAllOthers(left,msg);
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {    
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }


    Client actual = getClient(sc);

    // Decode and print the message to stdout
    String data = decoder.decode(buffer).toString();
    while((data.charAt(data.length()-1))!='\n'){
      buffer.clear();
      sc.read( buffer );
      buffer.flip();
      data+=decoder.decode(buffer).toString();
    }

    //System.out.println("string"+ data);

    /*int index = data.indexOf("EOF");
    System.out.println("index: " + index);
    while(index!=-1){
      String left = data.substring(0,index);
      buffer.clear();
      sc.read( buffer );
      buffer.flip();

      String right = decoder.decode(buffer).toString();
      System.out.println("here"+right);
      data = left + "" + right;
      index = data.indexOf("<CTRL-D>");
    }*/
    String[] message = data.split("\n");


    int size = message.length;
    for(int i = 0; i< size; i++){
      String[] verifyInput = message[i].split(" ");
      if(verifyInput.length == 1 && verifyInput[0].equals("/nick") || verifyInput.length == 1 && verifyInput[0].equals("/join")){
        actual.socket.write(ByteBuffer.wrap("ERROR\n".getBytes(charset)));
        return true;
      }
      switch(verifyInput[0]){
        // caso para criar nick
        case "/nick":
        //verificar se o nick escolhido existe 
          if(existsNick(verifyInput[1])){
            actual.socket.write(ByteBuffer.wrap("ERROR".getBytes(charset)));
            
          }
          else{
            System.out.println(actual);
            actual.socket.write(ByteBuffer.wrap("OK".getBytes(charset)));
            String oldNick = actual.nick;
            actual.nick = verifyInput[1];
            if(actual.state == "init")
              actual.state = "outside";
            if(actual.state.equals("inside")){
              String output = "NEWNICK " + oldNick + " " + actual.nick;
              msgAllOthers(actual,output);
            }
          }
          break;
        
          //entrar ou mudar de sala
        case "/join":
          if(actual.nick == null){
            actual.socket.write(ByteBuffer.wrap("ERROR".getBytes(charset)));
            break;
          }
          if(actual.state.equals("inside")){
            String leftMsg = "LEFT " + actual.nick;
            msgAllOthers(actual,leftMsg);
          }
          actual.sala = verifyInput[1];
          actual.socket.write(ByteBuffer.wrap("OK".getBytes(charset)));
          actual.state="inside";
          String joinedMsg = "JOINED " + actual.nick; 
          msgAllOthers(actual,joinedMsg);
        break;

          //leave na sala e envia msg aos q ficaram la 
        case "/leave":
          if(!actual.state.equals("inside")){
            actual.socket.write(ByteBuffer.wrap("ERROR".getBytes(charset)));
            break;
          }
          String leftMsg = "LEFT " + actual.nick;
          msgAllOthers(actual,leftMsg);
          actual.sala = null;
          actual.socket.write(ByteBuffer.wrap("OK".getBytes(charset)));
          actual.state="outside";
        break;

        //caso não esteja numa sala, o msgallothers não vai enviar mensagem p ninguem
        case "/bye":
          actual.socket.write(ByteBuffer.wrap("BYE".getBytes(charset)));
          System.out.println( "Closing connection to "+actual.socket );
          String byeMsg = "LEFT " + actual.nick;
          //enviar mensagem aos outros q ele saiu
          msgAllOthers(actual,byeMsg);
          // remover da lista de clientes
          clientList.remove(actual);
          //fechar a conexão
          actual.socket.close();
          return true;




        case "/priv":
          if(actual.nick==null || !existsNick(verifyInput[1])){ 
            actual.socket.write(ByteBuffer.wrap("ERROR".getBytes(charset)));
            break;
          }
          int start = verifyInput[0].length() + verifyInput[1].length() + 2;
          String msg = message[i].substring(start,message[i].length());
          privMsg(verifyInput[1],msg,actual);
          break;

        default :
          if(message[i].charAt(0)=='/')
            message[i] = message[i].substring(1,message[i].length());
            if(actual.nick!=null && actual.sala!=null){
              String speaking = "MESSAGE " + actual.nick + " " + message[i];
              for(Client c : clientList ){
                if(actual.sala.equals(c.sala)){
                    c.socket.write(ByteBuffer.wrap(speaking.getBytes(charset)));
                  }
              }
            }
            else if(actual.nick==null || actual.sala==null)
              actual.socket.write(ByteBuffer.wrap("ERROR".getBytes(charset)));

        break;    
      }
    }
    return true; 
  }


  static void privMsg(String nick, String message,Client emissor) throws IOException {
    //message += "\n";
    Client actual = getClientWName(nick);
    String send = "PRIVATE " + emissor.nick + " " + message;
    actual.socket.write(ByteBuffer.wrap(send.getBytes(charset)));
  }


  static void msgAllOthers(Client actual,String message) throws IOException{
    //message += "\n";
    for(Client c : clientList ){
      if(c!=actual){
        if(actual.sala!=null){
          if(actual.sala.equals(c.sala)){
              c.socket.write(ByteBuffer.wrap(message.getBytes(charset)));
          }
        }
      }
    }
  }



  static Client getClient(SocketChannel sc){
    for(Client c: clientList){
        if(c.socket==sc){
          return c;
        }
      }
    return null;
  }

   static Client getClientWName(String nick){
    for(Client c: clientList){
      if(c.nick!=null){
        if(c.nick.equals(nick)){
          return c;
        }
      }
    }
    return null;
  }



  static Boolean existsNick(String nick){
    for(Client c : clientList){
      if(c.nick!=null){
        if(c.nick.equals(nick) ){
          return true;
        }
      }
    }
    return false;
  }


}
class Client {
  SocketChannel socket;
  String state;
  String sala;
  String nick;
  
  Client(SocketChannel sc){
    this.socket = sc;
    this.state = "init";
  }
}