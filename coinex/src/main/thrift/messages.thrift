namespace java com.coinport.coinex.messages

enum Currency {
	RMB = 1
	LTC = 2
}

struct Bonk {
	1: string message
	2: i32 code
}