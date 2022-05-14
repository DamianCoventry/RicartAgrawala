import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class UdpTransport implements ITransport {
    private static final int NUM_RECEIVE_BUFFER_BYTES = 1024;
    private final DatagramSocket _socket;

    public UdpTransport(InetAddress localAddress, int port) throws SocketException {
        _socket = new DatagramSocket(port, localAddress);
    }

    @Override
    public String getAddressString() {
        return _socket.getLocalAddress().getHostAddress() + ":" + _socket.getLocalPort();
    }

    @Override
    public InetAddress getAddress() {
        return _socket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return _socket.getLocalPort();
    }

    @Override
    public void send(Message message) throws IOException {
        byte[] bytes = message.getPayload().toJSON().getBytes(StandardCharsets.UTF_8);
        _socket.send(new DatagramPacket(bytes, bytes.length, message.getAddress(), message.getPort()));
    }

    @Override
    public Message receive() throws IOException {
        byte[] bytes = new byte[NUM_RECEIVE_BUFFER_BYTES];
        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);

        _socket.receive(datagramPacket);
        byte[] receivedBytes = Arrays.copyOfRange(datagramPacket.getData(), 0, datagramPacket.getLength());

        Payload payload = Payload.fromJSON(new String(receivedBytes, StandardCharsets.UTF_8));
        return new Message(datagramPacket.getAddress(), datagramPacket.getPort(), payload);
    }
}
