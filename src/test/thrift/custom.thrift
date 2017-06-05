namespace java custom

service CustomService {
        string say(1:string text, 2:string text2)

        string withoutArgs()

        string withError()

        string withCollision(3333: string input)
}