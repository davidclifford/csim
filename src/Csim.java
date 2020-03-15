import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import javax.swing.*;
import javax.swing.text.DefaultCaret;

/*
 * This is an example of a simple windowed render loop
 */
public class Csim {

    static Graphics2D g2d = null;
    static String keys = "";

    public static void main( String[] args ) {

        int  xsize = 1280;
        int  ysize = 960;

        // Create game window...
        JFrame frame = new JFrame();
        frame.setIgnoreRepaint( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        JFrame tty = new JFrame();
        tty.setIgnoreRepaint( true );
        tty.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        JPanel panel = new JPanel(true);
        Font font = new Font("Courier", Font.BOLD,12);
        JTextArea text = new JTextArea(50,100);
        text.setFont(font);
        text.setEditable(false);
        text.setBackground(Color.BLACK);
        text.setForeground(Color.WHITE);
        DefaultCaret caret = (DefaultCaret)text.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        text.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
//                System.out.println("keyTyped");
                keys = keys + e.getKeyChar();
            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
        text.setFocusable(true);
        JScrollPane scrollPane = new JScrollPane(text);
        panel.add(scrollPane);
        tty.setSize(xsize, ysize);
        tty.add(panel);
        tty.setVisible( true );
        tty.setState(JFrame.ICONIFIED);
        tty.setState(JFrame.NORMAL);

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

// Initialize simulation
        boolean debug = false;
        int PC = 0x8000;
        int A = 0;
        int B = 0;
        int AH = 0;
        int AL = 0;
        int IR = 0;
        int phase = 0;

        int ALUOP    = 0x001f;
        int LOADOP   = 0x0007;
        int LOADSHIFT = 5;
        int DBUSOP   = 0x0003;
        int DBUSSHIFT = 8;
        int JUMPOP   = 0x0007;
        int JUMPSHIFT = 10;
        int ARENA    = 0x0001;
        int ARSHIFT = 13; // Active low
        int PCINCR   = 0x0001;
        int PCSHIFT = 14;
        int USRESET  = 0x0001;
        int USSHIFT = 15; // Active low
        int IRSHIFT  = 4;
        int CSHIFT = 8;
        int VSHIFT = 9;
        int ZSHIFT = 10;
        int NSHIFT = 11;
        int DSHIFT = 12;
        int MEMRESULT = 0;
        int ALURESULT = 1;
        int UARTRESULT = 2;

        String [] ALUop = {
            "0",
            "A",
            "B",
            "-A",
            "-B",
            "A+1",
            "B+1",
            "A-1",
            "B-1",
            "A+B",
            "A+B+1",
            "A-B",
            "A-Bspecial",
            "B-A",
            "A-B-1",
            "B-A-1",
            "A*BHI",
            "A*BLO",
            "A/B",
            "A%B",
            "A<<B",
            "A>>BL",
            "A>>BA",
            "AROLB",
            "ARORB",
            "A&B",
            "A|B",
            "A^B",
            "!A",
            "!B",
            "ADIVB",
            "AREMB"
        };

        // Load in binary files for ALU, Decode, Rom and Ram
        byte[] ALURom = new byte [0x400000];
        byte[] DecodeRom = null;
        byte[] Rom = new byte [0x8000];
        byte[] Ram = new byte [0x8000];

        read_bytes("alu.bin", ALURom);

        try {
//            ALURom = Files.readAllBytes(Paths.get("alu.bin"));
            DecodeRom = Files.readAllBytes(Paths.get("27Cucode.bin"));
            Rom = Files.readAllBytes(Paths.get("instr.bin"));
            byte [] RAM = Files.readAllBytes(Paths.get("video_strings.bin"));
            System.arraycopy(RAM, 0, Ram, 0, RAM.length);
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }

        // Do simulation loop
//        System.out.printf("\n%06x ", ALURom.length);
//        for (int x = 0; x<1024000; x++) {
//            if (x%16 ==0) System.out.printf("\n%06x ", x);
//            System.out.printf("%02x ", ALURom[x]);
//        }

        while( true ) {
            try {
                g2d = bi.createGraphics();

                // Work out the decode ROM index
                int decodeidx = (IR << IRSHIFT) | phase;
                // Get the microinstruction
                int uinst = (((DecodeRom[decodeidx*2+1]&0xff) << 8) | (DecodeRom[decodeidx*2])&0xff);

                boolean carry = false;
                boolean overflow = false;
                boolean zero = false;
                boolean negative = false;
                boolean divbyzero = false;

                // Decode the microinstruction
                int aluop = uinst & ALUOP;
                int loadop = (uinst >> LOADSHIFT) & LOADOP;
                int dbusop = (uinst >> DBUSSHIFT) & DBUSOP;
                int jumpop = (uinst >> JUMPSHIFT) & JUMPOP;
                int arena = (uinst >> ARSHIFT) & ARENA;
                int pcincr = (uinst >> PCSHIFT) & PCINCR;
                int usreset = (uinst >> USSHIFT) & USRESET;
                if (debug) {
                    System.out.printf("PC %04x IR %02x p %01x ui %04x upa %d%d%d \n",
                            PC, IR, phase, uinst, usreset, pcincr, arena);
                }

                // Do the ALU operation.
                int databus = 0;
                if (dbusop == ALURESULT) {
                    int alu_addr = ((aluop << 16) | (A << 8) | B) * 2;
                    int aluresult = ((ALURom[alu_addr+1]&0xff) << 8) | (ALURom[alu_addr]&0xff);
                    if (debug) {
                        System.out.printf("AB %02x %02x %s %04x \n", A, B, ALUop[aluop], aluresult);
                    }

                    // Extract the flags from the result, and remove from the result
                    carry = ((aluresult >> CSHIFT) & 1) == 1;
                    overflow = ((aluresult >> VSHIFT) & 1) == 1;
                    zero = ((aluresult >> ZSHIFT) & 1) == 1;
                    negative = ((aluresult >> NSHIFT) & 1) == 1;
                    divbyzero = ((aluresult >> DSHIFT) & 1) == 1;
                    if (debug) {
                        System.out.printf("FL %b %b %b %b %b\n", carry, overflow, zero, negative, divbyzero);
                    }
                    databus = aluresult & 0xff;
                }

                // Determine the address on the address bus: AR or PC
                int address = 0;
                if (arena == 0) {
                    address = (AH << 8)&0xff | AL;
                    if (debug) {
                        System.out.printf("AR %02x%02x\n", AH, AL);
                    }
                } else {
                    address = PC;
                    if (debug) {
                        System.out.printf("PC %04x \n", PC);
                    }
                }

                // Get the memory value
                if (dbusop == MEMRESULT) {
                    if (address >= 0x8000)
                        databus = Ram[address-0x8000]&0xff;
                    else
                        databus = Rom[address]&0xff;
                }

                // Read UART
                 if (dbusop == UARTRESULT) {
                     if (keys.length() > 0) {
                         databus = keys.charAt(0);
                         if (loadop != 0) {
                             keys = keys.substring(1);
                         }
                     } else {
                         databus = 0;
                     }
                 }

                 if (debug)
                    System.out.printf("dop %x dbus %02x \n", dbusop, databus);

                 //Load from the data bus
                if (loadop == 1) {
                    IR = databus;
                    if (debug)
                        System.out.printf("->IR %02x\n", IR);
                }
                if (loadop == 2) {
                    A = databus;
                    if (debug)
                        System.out.printf("->A %02x\n", A);
                }
                if (loadop == 3) {
                    B = databus;
                    if (debug)
                        System.out.printf("->B %02x\n", B);
                }
                if (loadop == 4) {
                    if (address >= 0x8000) {
                        Ram[address - 0x8000] = (byte) databus;
                        if (debug)
                            System.out.printf("->RAM %04x %02x\n", address - 0x8000, Ram[address - 0x8000]);
                    } else {
                        plot(address, databus);
                    }
                }
                if (loadop == 5) {
                    AH = databus;
                    if (debug)
                        System.out.printf("->AH %02x\n", AH);
                }
                if (loadop == 6) {
                    AL = databus;
                    if (debug)
                        System.out.printf("->AL %02x\n", AL);
                }
                if (loadop == 7) {
                    System.out.printf("%c", databus); // Flush the output
                    text.append(""+(char)databus);
                    if (debug)
                        System.out.printf("->IO %c", databus);
                }

                //    Increment the PC and the phase
                if (pcincr == 1)
                    PC = PC + 1;
                if (usreset == 0) {
                    phase = 0;
                } else {
                    phase = (phase+1) & 0xf;
                }

                //    Do any jumps
                if (jumpop == 1 && carry) {
                    PC = address;
                    if (debug)
                        System.out.print("JC ");
                }

                if (jumpop == 2 && overflow) {
                    PC = address;
                    if (debug)
                        System.out.print("JO ");
                }
                if (jumpop == 3 && zero) {
                    PC = address;
                    if (debug)
                        System.out.print("JZ ");
                }
                if (jumpop == 4 && negative) {
                    PC = address;
                    if (debug)
                        System.out.print("JN ");
                }
                if (jumpop == 5 && divbyzero) {
                    PC = address;
                    if (debug)
                        System.out.print("JD ");
                }
                if (jumpop == 7 && keys.length() == 0) {
                    PC = address;
                    if (debug)
                        System.out.print("JI ");
                }
                //    Exit if PC goes to $FFFF
                if (PC == 0xffff) {
                    if (debug)
                        System.out.println();
                    break;
                }

/////////////////////////////////
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

    static private void plot(int addr, int colour) {
        final int size = 8;
        int x = addr & 0xFF;
        int y = addr >> 8;
        int r = ((colour>>4)&3)<<6;
        int g = ((colour>>2)&3)<<6;
        int b = ((colour&3)<<6);
        g2d.setColor(new Color(r,g,b));
        g2d.fillRect(x*size, y*size, size, size);
    }

    static private void read_bytes(String filename, byte data[]) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
            int addr = 0;
            while (true) {
                data[addr++] = (byte) in.readUnsignedByte();
            }
        } catch (EOFException e) {
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
