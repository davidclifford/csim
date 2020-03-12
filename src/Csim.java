import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.swing.*;

/*
 * This is an example of a simple windowed render loop
 */
public class Csim {

    static Graphics2D g2d = null;

    public static void main( String[] args ) {

        int  xsize = 1280;
        int  ysize = 960;

        // Create game window...
        JFrame frame = new JFrame();
        frame.setIgnoreRepaint( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        JFrame tty = new JFrame();
        JPanel panel = new JPanel(true);
        JTextArea text = new JTextArea(100,100);
        panel.add(text);
        tty.setSize(xsize, ysize);
        tty.add(panel);
        tty.setVisible( true );


        // Create canvas for painting...
        Canvas canvas = new Canvas();
        canvas.setIgnoreRepaint( true );
        canvas.setSize( xsize, ysize );

        // Add canvas to game window...
        frame.add( canvas );
        frame.pack();
        frame.setVisible( true );

        // Create BackBuffer...
        canvas.createBufferStrategy( 2 );
        BufferStrategy buffer = canvas.getBufferStrategy();

        // Get graphics configuration...
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        // Create off-screen drawing surface
        BufferedImage bi = gc.createCompatibleImage( xsize, ysize );

        // Objects needed for rendering...
        Graphics graphics = null;
        Random rand = new Random();
        Color background = Color.BLACK;
        g2d = bi.createGraphics();
        g2d.setColor( background );
        g2d.fillRect( 0, 0, xsize, ysize );

        while( true ) {
            try {
                g2d = bi.createGraphics();
// Plot below here
                int x = rand.nextInt(160);
                int y = rand.nextInt(120);
                int c = rand.nextInt(64);
                plot(y<<8 | x, c);
// Plot something above here
// Do graphics
                // Blit image and flip...
                graphics = buffer.getDrawGraphics();
                graphics.drawImage( bi, 0, 0, null );
                if( !buffer.contentsLost() )
                    buffer.show();

                // Let the OS have a little time...
                Thread.yield();
            } finally {
                // release resources
                if( graphics != null )
                    graphics.dispose();
                if( g2d != null )
                    g2d.dispose();
            }
        }
    }

    static private void plot(int address, int colour) {
        final int size = 8;
        int x = address & 0xFF;
        int y = address >> 8;
        int r = ((colour>>4)&3)<<6;
        int g = ((colour>>2)&3)<<6;
        int b = ((colour&3)<<6);
        g2d.setColor(new Color(r,g,b));
        g2d.fillRect(x*size, y*size, size, size);
    }
}
/////////////////////////////////////
//public class Test2 {
//    public static void main(String[] args) {
//        JFrame ablak = new JFrame("Snake game");
//        ablak.setVisible(true);
//        ablak.setSize(new Dimension(600,600));
//        ablak.setFocusable(true);
//        ablak.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        ablak.addKeyListener(new KeyListener(){
//             @Override
//                public void keyPressed(KeyEvent e) {
//                    if(e.getKeyCode() == KeyEvent.VK_UP){
//                        System.out.println("Hi");
//                    }
//                }
//
//                @Override
//                public void keyTyped(KeyEvent e) {
//                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//                }
//
//                @Override
//                public void keyReleased(KeyEvent e) {
//                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//                }
//        });
//        ablak.setVisible(true);
//    }
//}
