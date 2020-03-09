import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.swing.JFrame;

/*
 * This is an example of a simple windowed render loop
 */
public class Csim {

    public static void main( String[] args ) {

        int  xsize = 1280;
        int  ysize = 960;
        // Create game window...
        JFrame app = new JFrame();
        app.setIgnoreRepaint( true );
        app.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        // Create canvas for painting...
        Canvas canvas = new Canvas();
        canvas.setIgnoreRepaint( true );
        canvas.setSize( xsize, ysize );

        // Add canvas to game window...
        app.add( canvas );
        app.pack();
        app.setVisible( true );

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
        Graphics2D g2d = null;
        Random rand = new Random();
        Color background = Color.BLACK;
        g2d = bi.createGraphics();
        g2d.setColor( background );
        g2d.fillRect( 0, 0, xsize, ysize );

        while( true ) {
            try {
                g2d = bi.createGraphics();



// Plot something before here
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

    static private void plot(int address, int colour, Graphics2D g2d) {
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
////////////////////////////////////
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JTextField;

public class Main {
  public static void main(String[] argv) throws Exception {
    JTextField component = new JTextField();
    component.addKeyListener(new MyKeyListener());

    JFrame f = new JFrame();

    f.add(component);
    f.setSize(300, 300);
    f.setVisible(true);

  }
}

class MyKeyListener extends KeyAdapter {
  public void keyPressed(KeyEvent evt) {
    if (evt.getKeyChar() == 'a') {
      System.out.println("Check for key characters: " + evt.getKeyChar());
    }
    if (evt.getKeyCode() == KeyEvent.VK_HOME) {
      System.out.println("Check for key codes: " + evt.getKeyCode());
    }
  }
}