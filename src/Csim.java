import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Scanner;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/*
 * This is an example of a simple windowed render loop
 */
public class Csim {

    static Graphics2D g2d = null;
    static String keys = "";
    static BufferedImage bi = null;
    static Graphics graphics = null;
    static BufferStrategy buffer = null;
    static int PC = 0x8000;
    static long time = 0;
    static char[] Vram = new char [0x8000];
    static Color backgroundColour = new Color(0,0,0);
    static boolean refresh = false;

    public static void main( String[] args ) {

        int  xsize = 1280;
        int  ysize = 960;

        boolean debug = false;
        boolean single = false;
        boolean video = true;

        String executable = "stripes.bin";
        if (args.length > 0) {
            for (String arg: args){
                if (arg.equals("-m")) {
                    PC = 0x0000;
                } else if (arg.equals("-d")) {
                    debug = true;
                } else if (arg.equals("-s")) {
                    single = true;
                } else if (arg.equals("-v")) {
                    video = false;
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
        if (!video) frame.setState(Frame.ICONIFIED);

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
//                keys = keys + e.getKeyChar();
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
                int d = e.getKeyChar();
                if (d == 19) { // ^S - Save screen
                    save_screen();
                } else if (d == 27) { // Esc - Reset CPU
                    keys = "";
                    PC = 0;
                } else if (d == 12) { // ^L - Load text file
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                    FileFilter hexFileFilter =new FileNameExtensionFilter("Hex files", "hex");
                    fileChooser.addChoosableFileFilter(hexFileFilter);
                    fileChooser.setFileFilter(hexFileFilter);
                    int result = fileChooser.showOpenDialog(canvas);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        try {
                            String newKeys = "";
                            File selectedFile = fileChooser.getSelectedFile();
                            FileReader fr = new FileReader(selectedFile);   //Creation of File Reader object
                            BufferedReader br = new BufferedReader(fr);  //Creation of BufferedReader object
                            int c = 0;
                            while ((c = br.read()) != -1)         //Read char by Char
                            {
                                char character = (char) c;          //converting integer to char
                                newKeys = newKeys + character;
                            }
                            keys = keys + newKeys;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                } else {
                    keys = keys + e.getKeyChar();
                }
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

        // Load in binary files for ALU, Decode, Rom, Ram and Vram
        char[] ALURom = new char [0x400000];
        char[] DecodeRom = new char [0x20000];
        char[] Rom = new char [0x8000];
        char[] Ram = new char [0x8000];


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
                if (single) {
                    // Wait one second
                    wait(1000);
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
                        plot(address, databus,  Vram[address]);
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
                    refreshScreen();
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

    static private void refreshScreen(){
        for (int y=0; y<120; y++) {
            for (int x=0; x<160; x++) {
                int addr = y<< 8 | x;
                plot(addr, Vram[addr], 0);
            }
        }
        refresh = false;
    }

    static private void plot(int addr, int colour, int current) {
        final int size = 8;
        final int half = size/2;
        int x = addr & 0xFF;
        int y = addr >> 8;

        // Is current pixel a background pixel?
        if (current >= 64 && current < 128)
            refresh = true;

        if (colour < 128) {
            if (colour < 64) {
                int r = ((colour >> 4) & 3)*85;
                int g = ((colour >> 2) & 3)*85;
                int b = ((colour & 3) * 85);
                g2d = bi.createGraphics();
                g2d.setColor(new Color(r, g, b));
                g2d.fillRect(x * size, y * size, size, size);
            } else {
                int r = ((colour >> 4) & 3)*85;
                int g = ((colour >> 2) & 3)*85;
                int b = ((colour & 3)*85);
                g2d = bi.createGraphics();
                Color col = new Color(r, g, b);
                g2d.setColor(col);
                g2d.fillRect(x * size, y * size, size, size);
                backgroundColour = col;
                refresh = true;
            }
        } else {
            int r = ((colour >> 6)&1)*255;
            int g = ((colour >> 5)&1)*255;
            int b = ((colour >> 4)&1)*255;
            // Use Orange as colour 0 (not black)
            if (r+g+b == 0) {
                r = 3<<6;
                g = 4<<4;
                b = 0;
            }
            g2d = bi.createGraphics();
            Color back = backgroundColour;
            if ((colour&1) > 0) {
                g2d.setColor(new Color(r, g, b));
                g2d.fillRect(x * size + half, y * size, half, half);
            } else {
                g2d.setColor(back);
                g2d.fillRect(x * size + half, y * size, half, half);
            }
            if ((colour&2) > 0) {
                g2d.setColor(new Color(r, g, b));
                g2d.fillRect(x * size, y * size, half, half);
            } else {
                g2d.setColor(back);
                g2d.fillRect(x * size, y * size, half, half);
            }
            if ((colour&4) > 0) {
                g2d.setColor(new Color(r, g, b));
                g2d.fillRect(x * size + half, y * size + half, half, half);
            } else {
                g2d.setColor(back);
                g2d.fillRect(x * size + half, y * size + half, half, half);
            }
            if ((colour&8) > 0) {
                g2d.setColor(new Color(r, g, b));
                g2d.fillRect(x * size, y * size + half, half, half);
            } else {
                g2d.setColor(back);
                g2d.fillRect(x * size, y * size + half, half, half);
            }
        }
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

    static private void save_screen() {
        String filename = "screen.bin";
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
            for (int address = 0; address < 0x8000; address++) {
                out.writeByte(Vram[address]);
            }
            out.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void wait(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
