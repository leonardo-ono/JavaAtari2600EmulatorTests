
// tests
// https://github.com/SingleStepTests/65x02/tree/main/6502

public class Cpu {
    
    public static final int ADDR_MASK = 0xffff;

    // ref.: https://www.pagetable.com/c64ref/6502/cpu_6502.txt
    public static int tc;
    public static boolean running;
    
    // registers
    public static int RA;
    public static int RX;
    public static int RY;
    public static int RS; // stack pointer
    public static int RP; // processor status
    public static int RPC; // program counter

    public static int incRS() {
        int oldRs = RS;
        RS = (RS + 1) & 0xff;
        return oldRs;
    }

    public static int decRS() {
        int oldRs = RS;
        RS = (RS - 1) & 0xff;
        return oldRs;
    }

    public static int incRPC() {
        int oldRpc = RPC;
        RPC = (RPC + 1) & ADDR_MASK;
        return oldRpc;
    }

    public static int decRPC() {
        int oldRpc = RPC;
        RPC = (RPC - 1) & ADDR_MASK;
        return oldRpc;
    }
        
    // flags
    public static final int FLAG_BIT_NEGATIVE = 7;  // N  Negative
    public static final int FLAG_BIT_OVERFLOW = 6;  // V  Overflow
    public static final int FLAG_BIT_EXPANSION = 5; // -  (Expansion)
    public static final int FLAG_BIT_BREAK_COMMAND = 4;   // B  Break Command
    public static final int FLAG_BIT_DECIMAL = 3;   // D  Decimal
    public static final int FLAG_BIT_INTERRUPT = 2; // I  Interrupt Disable
    public static final int FLAG_BIT_ZERO = 1;      // Z  Zero

    // obs: o bit C (Carry) eh setado (1) quando (RA >= fetch)
    //                                (0) quando (RA < fetch) ou seja 0 quando ha underflow
    //                                deixei essa observacao pois em outros processadores
    //                                parece ser invertido (normalmente 1 para overflow e 0 quando nao ha)
    public static final int FLAG_BIT_CARRY = 0;     // C  Carry    
    
    public static void setFlag(int bit, int value) {
        if (value == 1) {
            RP = RP | (1 << bit);
        }
        else if (value == 0) {
            RP = RP & ~(1 << bit);
        }
    }

    public static int getFlag(int bit) {
        return (RP & (1 << bit)) > 0 ? 1 : 0;
    }

    // addmodes
    public static final int __ = 0;     // -      1  -        Implied
    public static final int A = 1;      // A      1  A        Accumulator
    public static final int X = 2;      // X      1  X        X
    public static final int Y = 3;      // Y      1  Y        Y
    public static final int _d8 = 4;    // #d8    2  #$nn     Immediate

    public static final int a8 = 5;     // a8     2  $nn      Zero Page
    public static final int a8__ = 50;     // a8     2  $nn      Zero Page

    public static final int a8_X = 6;   // a8,X   2  $nn,X    X-Indexed Zero Page
    public static final int a8_X___ = 60;   // a8,X   2  $nn,X    X-Indexed Zero Page
    public static final int a8_Y = 7;   // a8,Y   2  $nn,Y    Y-Indexed Zero Page
    public static final int a8_Y___ = 70;   // a8,Y   2  $nn,Y    Y-Indexed Zero Page
    
    public static final int _a8_X_ = 8; // (a8,X) 2  ($nn,X)  X-Indexed Zero Page Indirect
    public static final int _a8_X_xxx = 80; // (a8,X) 2  ($nn,X)  X-Indexed Zero Page Indirect

    public static final int _a8_Y = 9;  // (a8),Y 2  ($nn),Y  Zero Page Indirect Y-Indexed
    public static final int _a8_Y_xxx = 90;  // (a8),Y 2  ($nn),Y  Zero Page Indirect Y-Indexed
    
    public static final int a16 = 10;   // a16    3  $nnnn    Absolute
    public static final int a16__ = 101;   // a16    3  $nnnn    Absolute

    public static final int a16_X = 11; // a16,X  3  $nnnn,X  X-Indexed Absolute
    public static final int a16_Xkkk = 110; // a16,X  3  $nnnn,X  X-Indexed Absolute
    public static final int a16_Y = 12; // a16,Y  3  $nnnn,Y  Y-Indexed Absolute
    public static final int a16_Ykkk = 120; // a16,Y  3  $nnnn,Y  Y-Indexed Absolute
    
    public static final int _a16_ = 13; // (a16)  3  ($nnnn)  Absolute Indirect
    public static final int _a16___ = 130; // (a16)  3  ($nnnn)  Absolute Indirect
    
    public static final int r8 = 14;    // r8     2  $nnnn    Relative
    
    private static int pag = 0;
    private static int rel = 0;

    public static int get(int op) {
        int tmp = 0;
        int tmp2 = 0;
        switch (op) {
            //case __: return 0; // -      1  -        Implied
            case A: return RA; // A      1  A        Accumulator
            case X: return RX; // A      1  X        X
            case Y: return RY; // A      1  Y        Y
            case _d8: return Bus.read(incRPC()); // #d8    2  #$nn     Immediate
            
            case a8: tmp = Bus.read(incRPC()); return Bus.read(tmp); // a8     2  $nn      Zero Page
            case a8__: tmp = Bus.read(incRPC()); return tmp; // a8     2  $nn      Zero Page

            case a8_X: tmp = Bus.read(incRPC()); return Bus.read((tmp + RX) & 0xff); // a8,X   2  $nn,X    X-Indexed Zero Page
            case a8_Y: tmp = Bus.read(incRPC()); return Bus.read((tmp + RY) & 0xff); // a8,Y   2  $nn,Y    Y-Indexed Zero Page
            
            case _a8_X_: {
                tmp = Bus.read(incRPC());
                int zp_addr = (tmp + RX) & 0xff;
                int h = Bus.read((zp_addr + 1) & 0xff) << 8;
                int l = Bus.read(zp_addr); 
                return Bus.read((h + l) & ADDR_MASK); 
            } // (a8,X) 2  ($nn,X)  X-Indexed Zero Page Indirect
            
            case _a8_Y: {
                tmp = Bus.read(incRPC());
                tmp2 = Bus.read(tmp) + (Bus.read((tmp + 1) & 0xff) << 8); 
                
                boolean pageCross = (tmp2 & 0xFF00) != ((tmp2 + RY) & 0xFF00);
                if (pageCross) {
                    pag += 1; // ciclo extra
                }                

                return Bus.read(tmp2 + RY); // TODO verificar se precisa fazer um clamp -> (tmp2 + RY) & 0ff ???
                                            //      ou a soma tmp2 + RY pode alterar page?
                
            } // (a8),Y 2  ($nn),Y  Zero Page Indirect Y-Indexed
            
            case a16: tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); return tmp; // a16    3  $nnnn    Absolute
            case a16__: tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); return Bus.read(tmp); // a16    3  $nnnn    Absolute

            case a16_X: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); 

                boolean pageCross = (tmp & 0xFF00) != ((tmp + RX) & 0xFF00);
                if (pageCross) {
                    pag += 1; // ciclo extra
                }                

                return Bus.read((tmp + RX) & ADDR_MASK); // a16,X  3  $nnnn,X  X-Indexed Absolute
            }

            case a16_Y: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); 

                boolean pageCross = (tmp & 0xFF00) != ((tmp + RY) & 0xFF00);
                if (pageCross) {
                    pag += 1; // ciclo extra
                }                

                return Bus.read((tmp + RY) & ADDR_MASK); // a16,Y  3  $nnnn,Y  Y-Indexed Absolute
            }
            
            case _a16_: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8);
                tmp2 = Bus.read(tmp) + (Bus.read(tmp + 1) << 8); 
                return Bus.read(tmp2); 
            } // (a16)  3  ($nnnn)  Absolute Indirect

            case _a16___: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8);
                tmp2 = Bus.read(tmp & ADDR_MASK) + (Bus.read((tmp + 1) & ADDR_MASK) << 8); 
                return tmp2 & ADDR_MASK;  
            } // (a16)  3  ($nnnn)  Absolute Indirect
            
            case r8: {
                rel = 1;
                tmp = (byte) Bus.read(incRPC());
                tmp = tmp + RPC;
                if ((tmp & 0xff00) != (RPC & 0xff00)) rel++;
                return tmp; // r8     2  $nnnn    Relative
            }
        }   
        return 0; 
    }

    public static void set(int op, int value) {
        value = value & 0xff;
        int tmp = 0;
        int tmp2 = 0;
        switch (op) {
            //case __: 0; // -      1  -        Implied
            case A: RA = value; break; // A      1  A        Accumulator
            case X: RX = value; break; // A      1  X        X
            case Y: RY = value; break; // A      1  Y        Y
            //case _d8: 0; // #d8    2  #$nn     Immediate
            
            case a8__: tmp = Bus.read(incRPC()); Bus.write(tmp, value); break; // a8     2  $nn      Zero Page
            case a8: tmp = Bus.read(RPC - 1); Bus.write(tmp, value); break; // a8     2  $nn      Zero Page
            // ASL(a8) faz um fetch -> pc++
            //         depois se fizer set(a8, value) - o pc vai incr novamente e isso fica errado
            //                                          ao gravar no a8, ele nao incrementa mais um RPC
            // --> workaround dentro de set(a8, r) usei (RPC - 1)

            case a8_X: {
                tmp = Bus.read(RPC - 1); 
                Bus.write((tmp + RX) & 0xff, value); 
                break; // a8,X   2  $nn,X    X-Indexed Zero Page
            }

            case a8_X___: {
                tmp = Bus.read(incRPC()); 
                Bus.write((tmp + RX) & 0xff, value); 
                break; // a8,X   2  $nn,X    X-Indexed Zero Page
            }
            
            case a8_Y: {
                tmp = Bus.read(RPC - 1); 
                Bus.write((tmp + RY) & 0xff, value); 
                break; // a8,Y   2  $nn,Y    Y-Indexed Zero Page
            }
            
            case a8_Y___: {
                tmp = Bus.read(incRPC()); 
                Bus.write((tmp + RY) & 0xff, value); 
                break; // a8,Y   2  $nn,Y    Y-Indexed Zero Page
            }

            case _a8_X_: {
                tmp = Bus.read(RPC - 1) + RX; 
                tmp2 = Bus.read(tmp & 0xff) + (Bus.read((tmp + 1) & 0xff) << 8); 
                Bus.write(tmp2, value); 
                break; // (a8,X) 2  ($nn,X)  X-Indexed Zero Page Indirect
            }

            case _a8_X_xxx: {
                tmp = Bus.read(incRPC()) + RX; 
                tmp2 = Bus.read(tmp & 0xff) + (Bus.read((tmp + 1) & 0xff) << 8); 
                Bus.write(tmp2, value); 
                break; // (a8,X) 2  ($nn,X)  X-Indexed Zero Page Indirect
            }

            case _a8_Y: {
                tmp = Bus.read(incRPC()); 
                tmp2 = Bus.read(tmp) + (Bus.read((tmp + 1) & 0xff) << 8);  
                Bus.write(tmp2 + RY, value); 

                break; // (a8),Y 2  ($nn),Y  Zero Page Indirect Y-Indexed
            }
            case _a8_Y_xxx: {
                tmp = Bus.read(RPC - 1); 
                tmp2 = Bus.read(tmp); 
                Bus.write((tmp2 + RY) & 0xff, value); 
                break; // (a8),Y 2  ($nn),Y  Zero Page Indirect Y-Indexed
            }
            
            case a16: ; tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); Bus.write(tmp, value); break; // a16    3  $nnnn    Absolute
            case a16__: tmp = Bus.read(RPC - 2) + (Bus.read(RPC - 1) << 8); Bus.write(tmp, value); break; // a16    3  $nnnn    Absolute
            // ASL(a16) inves de usar incRPC(), alterei para (RPC - 2) e (RPC - 1)

            case a16_X: {
                tmp = Bus.read(RPC - 2) + (Bus.read(RPC - 1) << 8); 
                Bus.write((tmp + RX) & ADDR_MASK, value); 
                break; // a16,X  3  $nnnn,X  X-Indexed Absolute
            }
            case a16_Xkkk: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); 
                Bus.write((tmp + RX) & ADDR_MASK, value); 
                break; // a16,X  3  $nnnn,X  X-Indexed Absolute
            }

            case a16_Y: {
                tmp = Bus.read(RPC - 2) + (Bus.read(RPC - 1) << 8); 
                Bus.write((tmp + RY) & ADDR_MASK, value); 
                break; // a16,Y  3  $nnnn,Y  Y-Indexed Absolute
            }
            case a16_Ykkk: {
                tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); 
                Bus.write((tmp + RY) & ADDR_MASK, value); 
                break; // a16,Y  3  $nnnn,Y  Y-Indexed Absolute
            }
            
            case _a16_: tmp = Bus.read(incRPC()) + (Bus.read(incRPC()) << 8); tmp2 = Bus.read(tmp) + (Bus.read(tmp + 1) << 8); Bus.write(tmp2, value); break; // (a16)  3  ($nnnn)  Absolute Indirect
            //case r8: 0; // r8     2  $nnnn    Relative
        }    
    }

    // note:
    // https://www.pagetable.com/c64ref/6502/cpu_6502.txt
    // https://web.archive.org/web/20021009074959/http://www.obelisk.demon.co.uk/6502/reference.html#ASL
    //
    // https://www.masswerk.at/6502/6502_instruction_set.html#opcodes-footnote1 <-- esse da para extrair os dados de quando precisa adicionar ciclos a mais
    //
    // o numero de ciclos pode variar dependendo do "address mode" (p) e da propria "instrucao" (t)
    // por exemplo: 
    // BCC - Branch if Carry Clear 2 (+1 if branch succeeds +2 if to a new page)
    // 90  2+rel
    // significa que se no processo do "address mode" o page ultrapassar, adiciona +1 ao clock (+1 if page crossed)
    //
    // fazendo uma verificacao, aparentemente somente esses 3 "address modes" abaixo podem ter "page crossed" (p)
    // Absolute,X	(+1 if page crossed)
    // Absolute,Y	(+1 if page crossed)
    // (Indirect),Y (+1 if page crossed)    
    // 
    // mas cuidado as instrucoes como INC, LSR, etc tem o "adress mode" "Absolute,X"
    // porem nao soma +1 se "page crossed"
    // ou seja, nao se pode simplesmente adicionar: if (page_crossed) then tc += 1 dentro do "Absolute,X"
    //
    // STA eh outro exemplo que possui "Absolute,X", "Absolute,Y" e "(Indirect),Y"
    // mas nenhum deles faz cycles += 1 se "page crossed"
    //
    // ref.: BCC
    // https://www.pagetable.com/c64ref/6502/?tab=2#BCC
    // https://github.com/OneLoneCoder/olcNES/blob/master/Part%232%20-%20CPU/olc6502.cpp#L776
    // https://www.masswerk.at/6502/6502_instruction_set.html#opcodes-footnote2
    // http://www.obelisk.demon.co.uk/6502/reference.html
    // https://www.pagetable.com/c64ref/6502/cpu_6502.txt
    //
    // acabei de verificar, 
    // em https://www.pagetable.com/c64ref/6502/cpu_6502.txt
    // os que tem '+pag' eh provavalmente "Absolute,X", "Absolute,Y" ou "(Indirect),Y"
    // se tiver '+rel' concerteza para "relative"

    // opcodes
    public static void executeNextInstruction() {
        if (Tia.wsyncHaltCpu) {
            tc += 1;
            return;
        }

        pag = 0;
        rel = 0;
        int opcode = Bus.read(incRPC());

        switch (opcode) {
            case 0x00: BRK(__); tc += 7; break; // 00  7
            case 0x01: ORA(_a8_X_); tc += 6; break; // (a8,X) 01  6
            
            case 0x04: NOP(__); tc += 3; break; // 04  3 // illegal opcode

            case 0x05: ORA(a8); tc += 3; break; // a8 05  3
            case 0x06: ASL(a8); tc += 5; break; // a8 06  5
            case 0x08: PHP(__); tc += 3; break; // 08  3
            case 0x09: ORA(_d8); tc += 2; break; // #d8 09  2
            case 0x0A: ASL(A); tc += 2; break; // A 0A  2
            case 0x0D: ORA(a16__); tc += 4; break; // a16 0D  4
            case 0x0E: ASL(a16__); tc += 6; break; // a16 0E  6
            case 0x10: BPL(r8); tc += 2 + rel; break; // r8 10  2+rel
            case 0x11: ORA(_a8_Y); tc += 5 + pag; break; // (a8),Y 11  5+pag
            case 0x15: ORA(a8_X); tc += 4; break; // a8,X 15  4
            case 0x16: ASL(a8_X); tc += 6; break; // a8,X 16  6
            case 0x18: CLC(__); tc += 2; break; // 18  2
            case 0x19: ORA(a16_Y); tc += 4 + pag; break; // a16,Y 19  4+pag
            case 0x1D: ORA(a16_X); tc += 4 + pag; break; // a16,X 1D  4+pag
            case 0x1E: ASL(a16_X); tc += 7; break; // a16,X 1E  7
            case 0x20: JSR(a16); tc += 6; break; // a16 20  6
            case 0x21: AND(_a8_X_); tc += 6; break; // (a8,X) 21  6
            case 0x24: BIT(a8); tc += 3; break; // a8 24  3
            case 0x25: AND(a8); tc += 3; break; // a8 25  3
            case 0x26: ROL(a8); tc += 5; break; // a8 26  5
            case 0x28: PLP(__); tc += 4; break; // 28  4
            case 0x29: AND(_d8); tc += 2; break; // #d8 29  2
            case 0x2A: ROL(A); tc += 2; break; // A 2A  2
            case 0x2C: BIT(a16__); tc += 4; break; // a16 2C  4
            case 0x2D: AND(a16__); tc += 4; break; // a16 2D  4
            case 0x2E: ROL(a16__); tc += 6; break; // a16 2E  6
            case 0x30: BMI(r8); tc += 2 + rel; break; // r8 30  2+rel
            case 0x31: AND(_a8_Y); tc += 5 + pag; break; // (a8),Y 31  5+pag
            case 0x35: AND(a8_X); tc += 4; break; // a8,X 35  4
            case 0x36: ROL(a8_X); tc += 6; break; // a8,X 36  6
            case 0x38: SEC(__); tc += 2; break; // 38  2
            case 0x39: AND(a16_Y); tc += 4 + pag; break; // a16,Y 39  4+pag
            case 0x3D: AND(a16_X); tc += 4 + pag; break; // a16,X 3D  4+pag
            case 0x3E: ROL(a16_X); tc += 7; break; // a16,X 3E  7
            case 0x40: RTI(__); tc += 6; break; // 40  6
            case 0x41: EOR(_a8_X_); tc += 6; break; // (a8,X) 41  6
            case 0x45: EOR(a8); tc += 3; break; // a8 45  3
            case 0x46: LSR(a8); tc += 5; break; // a8 46  5
            case 0x48: PHA(__); tc += 3; break; // 48  3
            case 0x49: EOR(_d8); tc += 2; break; // #d8 49  2
            case 0x4A: LSR(A); tc += 2; break; // A 4A  2
            case 0x4C: JMP(a16); tc += 3; break; // a16 4C  3
            case 0x4D: EOR(a16__); tc += 4; break; // a16 4D  4
            case 0x4E: LSR(a16__); tc += 6; break; // a16 4E  6
            case 0x50: BVC(r8); tc += 2 + rel; break; // r8 50  2+rel
            case 0x51: EOR(_a8_Y); tc += 5 + pag; break; // (a8),Y 51  5+pag
            case 0x55: EOR(a8_X); tc += 4; break; // a8,X 55  4
            case 0x56: LSR(a8_X); tc += 6; break; // a8,X 56  6
            case 0x58: CLI(__); tc += 2; break; // 58  2
            case 0x59: EOR(a16_Y); tc += 4 + pag; break; // a16,Y 59  4+pag
            case 0x5D: EOR(a16_X); tc += 4 + pag; break; // a16,X 5D  4+pag
            case 0x5E: LSR(a16_X); tc += 7; break; // a16,X 5E  7
            case 0x60: RTS(__); tc += 6; break; // 60  6
            case 0x61: ADC(_a8_X_); tc += 6; break; // (a8,X) 61  6
            case 0x65: ADC(a8); tc += 3; break; // a8 65  3
            case 0x66: ROR(a8); tc += 5; break; // a8 66  5
            case 0x68: PLA(__); tc += 4; break; // 68  4
            case 0x69: ADC(_d8); tc += 2; break; // #d8 69  2
            case 0x6A: ROR(A); tc += 2; break; // A 6A  2
            case 0x6C: JMP(_a16___); tc += 5; break; // (a16) 6C  5
            case 0x6D: ADC(a16__); tc += 4; break; // a16 6D  4
            case 0x6E: ROR(a16__); tc += 6; break; // a16 6E  6
            case 0x70: BVS(r8); tc += 2 + rel; break; // r8 70  2+rel
            case 0x71: ADC(_a8_Y); tc += 5 + pag; break; // (a8),Y 71  5+pag
            case 0x75: ADC(a8_X); tc += 4; break; // a8,X 75  4
            case 0x76: ROR(a8_X); tc += 6; break; // a8,X 76  6
            case 0x78: SEI(__); tc += 2; break; // 78  2
            case 0x79: ADC(a16_Y); tc += 4 + pag; break; // a16,Y 79  4+pag
            case 0x7D: ADC(a16_X); tc += 4 + pag; break; // a16,X 7D  4+pag
            case 0x7E: ROR(a16_X); tc += 7; break; // a16,X 7E  7
            case 0x81: STA(_a8_X_xxx); tc += 6; break; // (a8,X) 81  6
            case 0x84: STY(a8__); tc += 3; break; // a8 84  3
            case 0x85: STA(a8__); tc += 3; break; // a8 85  3
            case 0x86: STX(a8__); tc += 3; break; // a8 86  3
            case 0x88: DEY(__); tc += 2; break; // 88  2
            case 0x8A: TXA(__); tc += 2; break; // 8A  2
            case 0x8C: STY(a16); tc += 4; break; // a16 8C  4
            case 0x8D: STA(a16); tc += 4; break; // a16 8D  4
            case 0x8E: STX(a16); tc += 4; break; // a16 8E  4
            case 0x90: BCC(r8); tc += 2 + rel; break; // r8 90  2+rel
            case 0x91: STA(_a8_Y); tc += 6; break; // (a8),Y 91  6
            case 0x94: STY(a8_X___); tc += 4; break; // a8,X 94  4
            case 0x95: STA(a8_X___); tc += 4; break; // a8,X 95  4
            case 0x96: STX(a8_Y___); tc += 4; break; // a8,Y 96  4
            case 0x98: TYA(__); tc += 2; break; // 98  2
            case 0x99: STA(a16_Ykkk); tc += 5; break; // a16,Y 99  5
            case 0x9A: TXS(__); tc += 2; break; // 9A  2
            case 0x9D: STA(a16_Xkkk); tc += 5; break; // a16,X 9D  5
            case 0xA0: LDY(_d8); tc += 2; break; // #d8 A0  2
            case 0xA1: LDA(_a8_X_); tc += 6; break; // (a8,X) A1  6
            case 0xA2: LDX(_d8); tc += 2; break; // #d8 A2  2
            case 0xA4: LDY(a8); tc += 3; break; // a8 A4  3
            case 0xA5: LDA(a8); tc += 3; break; // a8 A5  3
            case 0xA6: LDX(a8); tc += 3; break; // a8 A6  3
            case 0xA8: TAY(__); tc += 2; break; // A8  2
            case 0xA9: LDA(_d8); tc += 2; break; // #d8 A9  2
            case 0xAA: TAX(__); tc += 2; break; // AA  2
            case 0xAC: LDY(a16__); tc += 4; break; // a16 AC  4
            case 0xAD: LDA(a16__); tc += 4; break; // a16 AD  4
            case 0xAE: LDX(a16__); tc += 4; break; // a16 AE  4
            case 0xB0: BCS(r8); tc += 2 + rel; break; // r8 B0  2+rel
            case 0xB1: LDA(_a8_Y); tc += 5 + pag; break; // (a8),Y B1  5+pag
            case 0xB4: LDY(a8_X); tc += 4; break; // a8,X B4  4
            case 0xB5: LDA(a8_X); tc += 4; break; // a8,X B5  4
            case 0xB6: LDX(a8_Y); tc += 4; break; // a8,Y B6  4
            case 0xB8: CLV(__); tc += 2; break; // B8  2
            case 0xB9: LDA(a16_Y); tc += 4 + pag; break; // a16,Y B9  4+pag
            case 0xBA: TSX(__); tc += 2; break; // BA  2
            case 0xBC: LDY(a16_X); tc += 4 + pag; break; // a16,X BC  4+pag
            case 0xBD: LDA(a16_X); tc += 4 + pag; break; // a16,X BD  4+pag
            case 0xBE: LDX(a16_Y); tc += 4 + pag; break; // a16,Y BE  4+pag
            case 0xC0: CPY(_d8); tc += 2; break; // #d8 C0  2
            case 0xC1: CMP(_a8_X_); tc += 6; break; // (a8,X) C1  6
            case 0xC4: CPY(a8); tc += 3; break; // a8 C4  3
            case 0xC5: CMP(a8); tc += 3; break; // a8 C5  3
            case 0xC6: DEC(a8); tc += 5; break; // a8 C6  5
            case 0xC8: INY(__); tc += 2; break; // C8  2
            case 0xC9: CMP(_d8); tc += 2; break; // #d8 C9  2
            case 0xCA: DEX(__); tc += 2; break; // CA  2
            case 0xCC: CPY(a16__); tc += 4; break; // a16 CC  4
            case 0xCD: CMP(a16__); tc += 4; break; // a16 CD  4
            case 0xCE: DEC(a16__); tc += 6; break; // a16 CE  6
            case 0xD0: BNE(r8); tc += 2 + rel; break; // r8 D0  2+rel
            case 0xD1: CMP(_a8_Y); tc += 5 + pag; break; // (a8),Y D1  5+pag
            case 0xD5: CMP(a8_X); tc += 4; break; // a8,X D5  4
            case 0xD6: DEC(a8_X); tc += 6; break; // a8,X D6  6
            case 0xD8: CLD(__); tc += 2; break; // D8  2
            case 0xD9: CMP(a16_Y); tc += 4 + pag; break; // a16,Y D9  4+pag
            case 0xDD: CMP(a16_X); tc += 4 + pag; break; // a16,X DD  4+pag
            case 0xDE: DEC(a16_X); tc += 7; break; // a16,X DE  7
            case 0xE0: CPX(_d8); tc += 2; break; // #d8 E0  2
            case 0xE1: SBC(_a8_X_); tc += 6; break; // (a8,X) E1  6
            case 0xE4: CPX(a8); tc += 3; break; // a8 E4  3
            case 0xE5: SBC(a8); tc += 3; break; // a8 E5  3
            case 0xE6: INC(a8); tc += 5; break; // a8 E6  5
            case 0xE8: INX(__); tc += 2; break; // E8  2
            case 0xE9: SBC(_d8); tc += 2; break; // #d8 E9  2
            case 0xEA: NOP(__); tc += 2; break; // EA  2
            case 0xEC: CPX(a16__); tc += 4; break; // a16 EC  4
            case 0xED: SBC(a16__); tc += 4; break; // a16 ED  4
            case 0xEE: INC(a16__); tc += 6; break; // a16 EE  6
            case 0xF0: BEQ(r8); tc += 2 + rel; break; // r8 F0  2+rel
            case 0xF1: SBC(_a8_Y); tc += 5 + pag; break; // (a8),Y F1  5+pag
            case 0xF5: SBC(a8_X); tc += 4; break; // a8,X F5  4
            case 0xF6: INC(a8_X); tc += 6; break; // a8,X F6  6
            case 0xF8: SED(__); tc += 2; break; // F8  2
            case 0xF9: SBC(a16_Y); tc += 4 + pag; break; // a16,Y F9  4+pag
            case 0xFD: SBC(a16_X); tc += 4 + pag; break; // a16,X FD  4+pag
            case 0xFE: INC(a16_X); tc += 7; break; // a16,X FE  7 

            default: {
                System.out.println("Illegal instruction " + opcode + "!");
            }
        }
        RPC = RPC & ADDR_MASK;
    }

    // mnemos
    
    public static void ADC(int op) {
        int f = get(op);
        int carry = getFlag(FLAG_BIT_CARRY);
        int tmp = (RA + f + carry);

        // Flags
        setFlag(FLAG_BIT_CARRY, (tmp & 0x100) != 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, (tmp & 0xff) == 0 ? 1 : 0);
        setFlag(FLAG_BIT_OVERFLOW, ((~(RA ^ f) & (RA ^ tmp)) & 0x80) != 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) != 0 ? 1 : 0);

        RA = tmp & 0xff;
    }
    
    public static void AND(int op) { //  Logical AND
        // AND  logic  N-----Z-  A ∧ M → A
        int r = get(A) & get(op);
        set(A, r);
        setFlag(FLAG_BIT_NEGATIVE, (r & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, r == 0 ? 1 : 0);
    }

    public static void ASL(int op) { //  Arithmetic Shift Left
        // ASL  shift  N-----ZC  C ← /M7...M0/ ← 0
        int v = get(op);
        int c = (v & 0x80) >> 7;
        int r = (v << 1) & 0xff;
        set(op, r); // --> nao pode fazer isso pois nessa funcao faz rpc++ e dai RPC fica errado
                    //     workaround dentro de set(a8, r) usei (RPC - 1)
        setFlag(FLAG_BIT_CARRY, c);
        setFlag(FLAG_BIT_ZERO, (r == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (r & 0x80) > 0 ? 1 : 0);
    }
    
    public static void BCC(int op) { //  Branch if Carry Clear
        // BCC  bra    --------  Branch on C = 0
        rel = 0;
        if (getFlag(FLAG_BIT_CARRY) == 0) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void BCS(int op) { //  Branch if Carry Set
        // BCS  bra    --------  Branch on C = 1
        rel = 0;
        if (getFlag(FLAG_BIT_CARRY) == 1) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void BEQ(int op) { //  Branch if Equal
        // BEQ  bra    --------  Branch on Z = 1
        rel = 0;
        if (getFlag(FLAG_BIT_ZERO) == 1) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    // https://www.pagetable.com/c64ref/6502/?tab=2#BCC
    public static void BIT(int op) { //  Bit Test
        // BIT  logic  NV----Z-  A ∧ M, M7 → N, M6 → V
        int v = get(op);
        int r = (RA & v) & 0xff;
        setFlag(FLAG_BIT_ZERO, r == 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (v & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_OVERFLOW, (v & 0x40) > 0 ? 1 : 0);
    }
    
    public static void BMI(int op) { //  Branch if Minus
        // BMI  bra    --------  Branch on N = 1
        rel = 0;
        if (getFlag(FLAG_BIT_NEGATIVE) == 1) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void BNE(int op) { //  Branch if Not Equal
        // BNE  bra    --------  Branch on Z = 0
        rel = 0;
        if (getFlag(FLAG_BIT_ZERO) == 0) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void BPL(int op) { //  Branch if Plus
        // BPL  bra    --------  Branch on N = 0
        rel = 0;
        if (getFlag(FLAG_BIT_NEGATIVE) == 0) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    // https://www.pagetable.com/c64ref/6502/?tab=2#BRK
    // TODO If an IRQ happens at the same time as a BRK instruction, the BRK instruction is ignored. ?
    // https://www.masswerk.at/6502/6502_instruction_set.html#break-flag
    public static void BRK(int op) { //  Force Interrupt
        //running = false;
        
        //if (!running) return;
        
        //[ [335, 122], [336, 132], [337, 139], [9684, 237], [35714, 0], [35715, 63], [35716, 247], [65534, 212], [65535, 37]]

//                 335
//        132 low  336
//        139 high 337

        // BRK  ctrl   -----1--  PC + 2↓, [FFFE] → PCL, [FFFF] → PCH
        incRPC();
        Bus.write(0x100 + (decRS()), (RPC >> 8) & 0xff);
        Bus.write(0x100 + (decRS()), RPC & 0xff);
        
        setFlag(FLAG_BIT_BREAK_COMMAND, 1);
        Bus.write(0x0100 + (decRS()), RP);
        setFlag(FLAG_BIT_BREAK_COMMAND, 0);
        setFlag(FLAG_BIT_INTERRUPT, 1);
        
        RPC = Bus.read(0xfffe & ADDR_MASK) + (Bus.read(0xffff & ADDR_MASK) << 8);
    }
    
    public static void BVC(int op) { //  Branch if Overflow Clear
        // BVC  bra    --------  Branch on V = 0
        rel = 0;
        if (getFlag(FLAG_BIT_OVERFLOW) == 0) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void BVS(int op) { //  Branch if Overflow Set
        // BVS  bra    --------  Branch on V = 1        
        rel = 0;
        if (getFlag(FLAG_BIT_OVERFLOW) == 1) {
            int addr = get(op);
            RPC = addr;
        }
        else {
            incRPC();
        }
    }
    
    public static void CLC(int op) { //  Clear Carry Flag
        // CLC  flags  -------0  0 → C
        setFlag(FLAG_BIT_CARRY, 0);
    }
    
    public static void CLD(int op) { //  Clear Decimal Mode
        // CLD  flags  ----0---  0 → D
        setFlag(FLAG_BIT_DECIMAL, 0);
    }
    
    public static void CLI(int op) { //  Clear Interrupt Disable
        // CLI  flags  -----0--  0 → I
        setFlag(FLAG_BIT_INTERRUPT, 0);
    }
    
    public static void CLV(int op) { //  Clear Overflow Flag
        // CLV  flags  -0------  0 → V
        setFlag(FLAG_BIT_OVERFLOW, 0);
    }
    
    public static void CMP(int op) { //  Compare
        // CMP  arith  N-----ZC  A - M
        int v = get(op);
        int tmp = (RA - v) & 0xff;
        setFlag(FLAG_BIT_CARRY, (RA >= v) ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void CPX(int op) { //  Compare X Register
        // CPX  arith  N-----ZC  X - M
        int v = get(op);
        int tmp = (RX - v) & 0xff;
        setFlag(FLAG_BIT_CARRY, (RX >= v) ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void CPY(int op) { //  Compare Y Register
        // CPY  arith  N-----ZC  Y - M
        int v = get(op);
        int tmp = (RY - v) & 0xff;
        setFlag(FLAG_BIT_CARRY, (RY >= v) ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void DEC(int op) { //  Decrement Memory
        // DEC  inc    N-----Z-  M - 1 → M
        int v = get(op);
        int tmp = (v - 1) & 0xff;
        set(op, tmp);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void DEX(int op) { //  Decrement X Register
        // DEX  inc    N-----Z-  X - 1 → X        
        int v = get(X);
        int tmp = (v - 1) & 0xff;
        set(X, tmp);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void DEY(int op) { //  Decrement Y Register
        // DEY  inc    N-----Z-  Y - 1 → Y
        int v = get(Y);
        RY = (v - 1) & 0xff;
        setFlag(FLAG_BIT_ZERO, (RY == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (RY & 0x80) > 0 ? 1 : 0);
    }
    
    public static void EOR(int op) { //  Exclusive OR
        // EOR  logic  N-----Z-  A ⊻ M → A
        RA = (RA ^ get(op));
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
    }
    
    public static void INC(int op) { //  Increment Memory
        // INC  inc    N-----Z-  M + 1 → M
        int v = get(op);
        int tmp = (v + 1) & 0xff;
        set(op, tmp);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void INX(int op) { //  Increment X Register
        // INX  inc    N-----Z-  X + 1 → X
        int v = get(X);
        int tmp = (v + 1) & 0xff;
        set(X, tmp);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void INY(int op) { //  Increment Y Register
        // INY  inc    N-----Z-  Y + 1 → Y
        int v = get(Y);
        int tmp = (v + 1) & 0xff;
        set(Y, tmp);
        setFlag(FLAG_BIT_ZERO, (tmp == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
    }
    
    public static void JMP(int op) { //  Jump
        if (op == _a16___) {
            // implementa o bug para instrução 6C JMP indirect para o NES
            int ptr = Bus.read(RPC) + (Bus.read((RPC + 1) & ADDR_MASK) << 8); // lê o endereço imediato (ex: $EAFF)
            int addr;
            if ((ptr & 0x00FF) == 0x00FF) { 
                // BUG do 6502: se cruza página, não soma o carry
                int lo = Bus.read(ptr);
                int hi = Bus.read(ptr & 0xFF00); // lê da mesma página!
                addr = (hi << 8) | lo;
            } else {
                addr = Bus.read(ptr) + (Bus.read((ptr + 1) & ADDR_MASK) << 8);
            }
            RPC = addr;            
        }
        else {
            // JMP  ctrl   --------  [PC + 1] → PCL, [PC + 2] → PCH
            int addr = get(op);
            RPC = addr;
        }
    }
    
    public static void JSR(int op) { //  Jump to Subroutine
        // JSR  ctrl   --------  PC + 2↓, [PC + 1] → PCL, [PC + 2] → PCH
        int addr = get(op); // addr=8fc2
        int retAddr = (RPC - 1) & ADDR_MASK;
        Bus.write(0x0100 + (decRS()), (retAddr >> 8) & 0xff); // 318=82
        Bus.write(0x0100 + (decRS()), retAddr & 0xff); //317=140
        RPC = addr;        
    }
    
    public static void LDA(int op) { //  Load Accumulator
        // LDA  load   N-----Z-  M → A
        RA = get(op);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
    }
    
    public static void LDX(int op) { //  Load X Register
        // LDX  load   N-----Z-  M → X
        RX = get(op);
        setFlag(FLAG_BIT_ZERO, RX == 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (RX & 0x80) > 0 ? 1 : 0);
    }
    
    public static void LDY(int op) { //  Load Y Register
        // LDY  load   N-----Z-  M → Y        
        RY = get(op);
        setFlag(FLAG_BIT_ZERO, RY == 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (RY & 0x80) > 0 ? 1 : 0);
    }
    
    public static void LSR(int op) { //  Logical Shift Right
        // LSR  shift  0-----ZC  0 → /M7...M0/ → C
        int v = get(op);
        int tmp = (v >> 1) & 0xff;	
        set(op, tmp);
        setFlag(FLAG_BIT_CARRY, v & 1);
        setFlag(FLAG_BIT_ZERO, (tmp & 0xff) == 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, 0);
    }
    
    public static void NOP(int op) { //  No Operation
        // NOP  nop    --------  No operation
    }
    
    public static void ORA(int op) { //  Logical OR
        // ORA  logic  N-----Z-  A ∨ M → A
        RA = RA | get(op);
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
    }
    
    public static void PHA(int op) { //  Push Accumulator
        // PHA  stack  --------  A↓
        Bus.write(0x0100 + (decRS()), RA);
    }
    
    // https://www.masswerk.at/6502/6502_instruction_set.html#BRK
    // note: The status register will be pushed with the break flag and bit 5 set to 1.
    public static void PHP(int op) { //  Push Processor Status
        // PHP  stack  --------  P↓
        setFlag(FLAG_BIT_BREAK_COMMAND, 1);
        setFlag(FLAG_BIT_EXPANSION, 1);
        Bus.write(0x0100 + (decRS()), RP);
        // TODO: verificar se eh necessario isso
        // no source do javidx9, ele depois limpa os flags BREAK e EXPANSION
        setFlag(FLAG_BIT_BREAK_COMMAND, 0);
        //setFlag(FLAG_BIT_EXPANSION, 0);
    }
    
    public static void PLA(int op) { //  Pull Accumulator
        // PLA  stack  N-----Z-  A↑
        incRS();
        RA = Bus.read(0x100 + RS);
        setFlag(FLAG_BIT_ZERO, (RA == 0) ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
    }

    public static void PLP(int op) { //  Pull Processor Status
        // PLP  stack  NV--DIZC  P↑        
        incRS();
        RP = Bus.read(0x100 + RS);
        // TODO verificar no javidx9, ele esta setando UNUSED (EXPANSION) flag
        setFlag(FLAG_BIT_EXPANSION, 1);
        setFlag(FLAG_BIT_BREAK_COMMAND, 0); // para passar nos testes, precisei zerar este break flag
    }
    
    public static void ROL(int op) { //  Rotate Left
        // ROL  shift  N-----ZC  C ← /M7...M0/ ← C
        int v = get(op);
        int nc = (v & 0x80) >> 7;
        int oc = getFlag(FLAG_BIT_CARRY);
        int r = ((v << 1) | oc) & 0xff;
        set(op, r);
        setFlag(FLAG_BIT_NEGATIVE, (r & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, r == 0 ? 1 : 0);
        setFlag(FLAG_BIT_CARRY, nc);
    }
    
    public static void ROR(int op) { //  Rotate Right
        // ROR  shift  N-----ZC  C → /M7...M0/ → C
        int v = get(op);
        int nc = v & 1;
        int oc = getFlag(FLAG_BIT_CARRY);
        int r = (v >> 1) | (oc << 7);
        set(op, r);
        setFlag(FLAG_BIT_NEGATIVE, (r & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, r == 0 ? 1 : 0);
        setFlag(FLAG_BIT_CARRY, nc);
    }

    public static void RTI(int op) {
        // Recupera P da pilha
        incRS();
        RP = Bus.read(0x0100 + RS);
        
        // Ajusta os bits específicos do 6507
        RP &= ~(1 << FLAG_BIT_BREAK_COMMAND); // BREAK = 0
        RP |=  (1 << FLAG_BIT_EXPANSION);    // UNUSED = 1

        // Recupera o PC da pilha
        incRS();
        int pcl = Bus.read(0x0100 + RS);

        incRS();
        int pch = Bus.read(0x0100 + RS);

        RPC = (pch << 8) | pcl;
    }
    
    public static void RTS(int op) { //  Return from Subroutine
        // RTS  ctrl   --------  PC↑, PC + 1 → PC
        incRS();
        RPC = Bus.read(0x0100 + RS);
        incRS();
        RPC += (Bus.read(0x0100 + RS) << 8);

        // TODO agora fiquei confuso 
        // precisa adicionar + 1? nos testes SIM, mas por que?
        incRPC();
    }
    
    public static void SBC(int op) { //  Subtract with Carry
        // SBC  arith  NV----ZC  A - M - ~C → A
        int f = get(op) & 0xffff;
        int v = f ^ 0x00ff;
        int tmp = RA + v + getFlag(FLAG_BIT_CARRY);
        setFlag(FLAG_BIT_CARRY, (tmp & 0xff00) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, (tmp & 0xff) == 0 ? 1 : 0);
        setFlag(FLAG_BIT_OVERFLOW, ((tmp ^ RA) & (tmp ^ v) & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_NEGATIVE, (tmp & 0x80) > 0 ? 1 : 0);
        RA = tmp & 0xff;        
    }
    
    public static void SEC(int op) { //  Set Carry Flag
        // SEC  flags  -------1  1 → C
        setFlag(FLAG_BIT_CARRY, 1);
    }
    
    public static void SED(int op) { //  Set Decimal Flag
        // SED  flags  ----1---  1 → D
        setFlag(FLAG_BIT_DECIMAL, 1);
    }
    
    public static void SEI(int op) { //  Set Interrupt Disable
        // SEI  flags  -----1--  1 → I
        setFlag(FLAG_BIT_INTERRUPT, 1);
    }
    
    public static void STA(int op) { //  Store Accumulator
        // STA  load   --------  A → M        
        
        set(op, RA);

        // o set(_a8_X_, value) nao ficou bom, STA(_a8_X_, value)
        // tem situacoes que precis [RPC++] e outras [RPC - 1] ???
    }
    
    public static void STX(int op) { //  Store X Register
        // STX  load   --------  X → M
        set(op, RX);
    }
    
    public static void STY(int op) { //  Store Y Register
        // STY  load   --------  Y → M
        set(op, RY);
    }
    
    public static void TAX(int op) { //  Transfer Accumulator to X
        // TAX  trans  N-----Z-  A → X
        RX = RA;
        setFlag(FLAG_BIT_NEGATIVE, (RX & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RX == 0 ? 1 : 0);
    }
    
    public static void TAY(int op) { //  Transfer Accumulator to Y
        // TAY  trans  N-----Z-  A → Y
        RY = RA;
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
    }
    
    public static void TSX(int op) { //  Transfer Stack Pointer to X
        // TSX  trans  N-----Z-  S → X
        RX = RS;
        setFlag(FLAG_BIT_NEGATIVE, (RX & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RX == 0 ? 1 : 0);
    }
    
    public static void TXA(int op) { //  Transfer X to Accumulator
        // TXA  trans  N-----Z-  X → A
        RA = RX;
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
    }
    
    public static void TXS(int op) { //  Transfer X to Stack Pointer
        // TXS  trans  --------  X → S        
        RS = RX;
    }
    
    public static void TYA(int op) { //  Transfer Y to Accumulator
        // TYA  trans  N-----Z-  Y → A
        RA = RY;
        setFlag(FLAG_BIT_NEGATIVE, (RA & 0x80) > 0 ? 1 : 0);
        setFlag(FLAG_BIT_ZERO, RA == 0 ? 1 : 0);
    }

    public static void reset() {
        tc = 0;
        running = true;
        
        RA = 0;
        RX = 0;
        RY = 0;
        RS = 0; // stack pointer
        RP = 0; // processor status
        RPC = 0; // program counter        
    }
    
    private static String toHex(int value, int n) {
        String hex = "000000" + Integer.toHexString(value);
        hex = hex.substring(hex.length() - n, hex.length());
        return hex;
    }

    public static String getRegsInf() {
        return toHex(RA, 2) + " " + toHex(RX, 2) + " " + toHex(RY, 2) + " " + toHex(RS, 2) + " " + toHex(RPC, 4) + " " + toHex(RP, 2);
    }
    
}
