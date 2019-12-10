import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.nio.channels.*;
import java.nio.charset.*;

import java.nio.ByteBuffer;

import java.awt.event.*;
import javax.swing.*;


public class ClientChat  implements Runnable  {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    static ByteBuffer buffer;
    private JTextField chatBox = new JTextField();
      static private final Charset charset = Charset.forName("UTF8");

    static private final CharsetDecoder decoder = charset.newDecoder();

    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui



    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    SocketChannel socket;
    public ClientChat(String server, int port)throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
        });
       Thread service = new Thread(this);
        this.buffer = ByteBuffer.allocate( 16384 );
        socket = SocketChannel.open(new InetSocketAddress(server, port));
       service.run();
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        buffer.clear();
        buffer.rewind();
        String msg = message+'\n';
        buffer.put(msg.getBytes());
        buffer.flip();
        socket.write(buffer);    
        buffer.clear();
        buffer.rewind();
    }



    
    // Método principal do objecto
    public void run()  {
        // PREENCHER AQUI 
            while(true){
              try{
                    buffer.clear();
                    socket.read( buffer );
                    buffer.flip();
                    buffer.rewind();

                    if (buffer.limit()==0) {
                        return ;
                    }



        // Decode and print the message to stdout
                String message = decoder.decode(buffer).toString();

                String[] analyse = message.split(" ");
                switch(analyse[0]){
                    case "OK":
                        System.out.println("OK");
                        break;
                    case "ERROR":
                        System.out.println("ERROR");
                        break;
                    case "MESSAGE":
                        message = message.substring(analyse[0].length()+analyse[1].length()+2,message.length());
                        String msg = analyse[1] + ": " + message;
                        printMessage(msg);
                        printMessage("\n");
                        break;
                    case "JOINED":
                        String joined = analyse[1] + " has joined the room!";
                        printMessage(joined);
                        printMessage("\n");
                        break;
                    case "LEFT":
                        String legt = analyse[1] + " has left the room!";
                        printMessage(message);
                        printMessage("\n");
                        break;
                    case "BYE":
                        System.out.println("BYE");
                        break;
                    case "NEWNICK":
                        //System.out.println("OK");
                        String newnick = analyse[1] + " mudou de nome para " + analyse[2];
                        printMessage(newnick);
                        printMessage("\n");
                        break;
                    case "PRIVATE":
                        String priv = "Private message from " + analyse[1] + ":";
                        String privmsg = message.substring("PRIVATE".length()+analyse[1].length() +2, message.length());
                        String privfinal = priv + " " + privmsg;
                        printMessage(privfinal);
                        printMessage("\n");
                        break;

                    default:
                        System.out.println("ERROR");
                        break;

                }
            }

                catch( IOException ie ) {
                    break;
                }
                

            }


    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ClientChat client = new ClientChat(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}