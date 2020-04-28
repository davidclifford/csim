import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import javax.swing.*;

/*
 * This is an example of a simple windowed render loop
 */
public class Csim {

    static Graphics2D g2d = null;
    static String keys = "";
    static BufferedImage bi = null;
    static Graphics graphics = null;
    static BufferStrategy buffer = null;
    static long time = 0;

    public static void main( String[] args ) {

        int  xsize = 1280;
        int  ysize = 960;

        boolean debug = false;
        int PC = 0x8000;

        String executable = "stripes.bin";
        if (args.length > 0) {
            for (String arg: args){
                if (arg.equals("-m")) {
                    PC = 0x0000;
                } else if (arg.equals("-d")) {
                    debug = true;
                } else if (arg.startsWith("-")) {
                        System.out.printf("Unknown option %s", arg);
                        System.exit(1);
                } else {
                    executable = arg;
                }
            }
        }

        // Create game window...
        JFrame frame = new JFrame();
        frame.setIgnoreRepaint( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

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
        buffer = canvas.getBufferStrategy();

        // Get graphics configuration...
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        // Create off-screen drawing surface
        bi = gc.createCompatibleImage( xsize, ysize );

        // Objects needed for rendering...
        Color background = Color.BLACK;
        g2d = bi.createGraphics();
        g2d.setColor( background );
        g2d.fillRect( 0, 0, xsize, ysize );

        frame.addKeyListener(new KeyListener() {
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
        frame.setFocusable(true);
        canvas.addKeyListener(new KeyListener() {
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
        canvas.setFocusable(true);

        // Initialize simulation
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
        int VIDRESULT = 3;

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
        char[] ALURom = new char [0x400000];
        char[] DecodeRom = new char [0x20000];
        char[] Rom = new char [0x8000];
        char[] Ram = new char [0x8000];
        char[] Vram = new char [0x8000];


        try {
            read_bytes("alu.bin", ALURom);
            read_bytes("27Cucode.rom", DecodeRom);
            read_bytes("instr.bin", Rom);
            read_bytes(executable, Ram);
        } catch(Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        while( true ) {
            try {
                // Work out the decode ROM index
                int decodeidx = (IR << IRSHIFT) | phase;
                // Get the microinstruction
                int uinst = ((((char)DecodeRom[decodeidx*2+1]) << 8) | ((char)DecodeRom[decodeidx*2]));

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
                    address = ((char)AH) << 8 | AL;
                    if (debug) {
                        System.out.printf("AR %02x%02x\n", AH, AL);
                        System.out.printf("AR %04x\n", address);
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
                        databus = (char)Ram[address-0x8000];
                    else
                        databus = (char)Rom[address];
                }

                // Get the video memory value
                if (dbusop == VIDRESULT) {
                    if (address >= 0x8000)
                        databus = (char)Vram[address-0x8000];
                    else
                        databus = (char)Vram[address];
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
                        Ram[address - 0x8000] = (char)databus;
                        if (debug)
                            System.out.printf("->RAM %04x %02x\n", address - 0x8000, (byte)Ram[address - 0x8000]);
                    } else {
                        plot(address, databus);
                        Vram[address] = (char)databus;
                        if (debug)
                            System.out.printf("->VRAM %04x %02x\n", address, (byte)Vram[address]);
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

                // Do graphics
                // Blit image and flip every so often
                if (System.currentTimeMillis() - time > 20) {
                    graphics = buffer.getDrawGraphics();
                    graphics.drawImage(bi, 0, 0, null);
                    if (!buffer.contentsLost())
                        buffer.show();
                    time = System.currentTimeMillis();
                }

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
        g2d = bi.createGraphics();
        g2d.setColor(new Color(r,g,b));
        g2d.fillRect(x*size, y*size, size, size);
    }

    static private void read_bytes(String filename, char data[]) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
            int addr = 0;
            while (true) {
                data[addr++] = (char) in.readUnsignedByte();
            }
        } catch (EOFException e) {
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
