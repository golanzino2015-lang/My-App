/* Starter lessons: thousands of small example chats generated from templates.
 * Every fact here is something the AI can learn. Edit freely to change what it knows.
 */
'use strict';
(function (root) {

const NAMES = ['alex', 'sam', 'maya', 'noah', 'emma', 'liam', 'olivia', 'leo', 'mia', 'ethan', 'ava', 'lucas',
  'zoe', 'adam', 'sara', 'daniel', 'lily', 'omar', 'nina', 'ben', 'yuki', 'david', 'amir', 'chloe', 'max',
  'ella', 'jack', 'ruby', 'ali', 'grace', 'tom', 'hana', 'eli', 'anna', 'ivan', 'luna', 'jake', 'nora',
  'ryan', 'iris', 'kai', 'rosa', 'sofia', 'theo', 'aria', 'mateo', 'jamal', 'priya', 'chen', 'lea', 'yosef',
  'dana', 'gal', 'tal', 'noa', 'itay', 'roni', 'shira', 'eitan', 'ori'];

const COLORS = [['the sky', 'blue'], ['grass', 'green'], ['the sun', 'yellow'], ['snow', 'white'],
  ['a banana', 'yellow'], ['coal', 'black'], ['a carrot', 'orange'], ['a lemon', 'yellow'],
  ['a strawberry', 'red'], ['a tomato', 'red'], ['milk', 'white'], ['chocolate', 'brown'],
  ['the ocean', 'blue'], ['a leaf', 'green'], ['a frog', 'green'], ['a pumpkin', 'orange'],
  ['a flamingo', 'pink'], ['a polar bear', 'white'], ['a crow', 'black'], ['a cherry', 'red'],
  ['an eggplant', 'purple'], ['a fire truck', 'red'], ['the moon', 'gray'], ['a zebra', 'black and white'],
  ['a panda', 'black and white'], ['an orange', 'orange'], ['blood', 'red'], ['a cucumber', 'green']];

const SOUNDS = [['dog', 'woof'], ['cat', 'meow'], ['cow', 'moo'], ['duck', 'quack'], ['sheep', 'baa'],
  ['pig', 'oink'], ['horse', 'neigh'], ['lion', 'roar'], ['owl', 'hoo hoo'], ['bee', 'buzz'],
  ['snake', 'hiss'], ['frog', 'ribbit'], ['mouse', 'squeak'], ['rooster', 'cock a doodle doo'],
  ['donkey', 'hee haw'], ['bird', 'tweet tweet'], ['chicken', 'cluck'], ['wolf', 'awoo'], ['goat', 'maa']];

const WHAT_IS = [
  ['a dog', 'a dog is an animal that barks. many people keep dogs as pets.'],
  ['a cat', 'a cat is a small furry animal that says meow. cats are popular pets.'],
  ['a fish', 'a fish is an animal that lives in water and breathes with gills.'],
  ['a bird', 'a bird is an animal with feathers and wings. most birds can fly.'],
  ['a cow', 'a cow is a big farm animal that gives us milk.'],
  ['a horse', 'a horse is a big strong animal that people can ride.'],
  ['a lion', 'a lion is a big wild cat. people call it the king of the jungle.'],
  ['an elephant', 'an elephant is the biggest land animal. it has a long trunk.'],
  ['a whale', 'a whale is a huge animal that lives in the ocean. it is a mammal, not a fish.'],
  ['a shark', 'a shark is a big fish with sharp teeth that lives in the ocean.'],
  ['a bee', 'a bee is a small flying insect that makes honey.'],
  ['a spider', 'a spider is a small animal with eight legs that spins webs.'],
  ['a penguin', 'a penguin is a bird that cannot fly, but it swims very well.'],
  ['a dinosaur', 'dinosaurs were giant reptiles that lived millions of years ago.'],
  ['an apple', 'an apple is a round fruit that can be red, green or yellow.'],
  ['a banana', 'a banana is a long yellow fruit.'],
  ['pizza', 'pizza is a food made of bread, tomato sauce and cheese.'],
  ['bread', 'bread is a food made from flour and water, baked in an oven.'],
  ['a car', 'a car is a machine with four wheels that people drive.'],
  ['a bus', 'a bus is a big vehicle that carries many people.'],
  ['a train', 'a train is a long vehicle that runs on tracks.'],
  ['a plane', 'a plane is a machine with wings that flies in the sky.'],
  ['a boat', 'a boat is a vehicle that floats and moves on water.'],
  ['a house', 'a house is a building where people live.'],
  ['a school', 'a school is a place where kids go to learn.'],
  ['a book', 'a book is a set of pages with words or pictures that you read.'],
  ['a phone', 'a phone is a device you use to call, text and use apps. i live in one!'],
  ['a computer', 'a computer is a machine that follows instructions very fast.'],
  ['the sun', 'the sun is a star. it gives the earth light and heat.'],
  ['the moon', 'the moon is a big ball of rock that goes around the earth.'],
  ['a star', 'a star is a giant ball of hot gas that gives off light.'],
  ['the earth', 'the earth is the planet we live on.'],
  ['a planet', 'a planet is a big round world that goes around a star.'],
  ['a tree', 'a tree is a tall plant with a trunk, branches and leaves.'],
  ['a flower', 'a flower is the colorful part of a plant.'],
  ['rain', 'rain is water that falls from clouds.'],
  ['snow', 'snow is frozen water that falls from clouds when it is cold.'],
  ['water', 'water is a clear liquid. all living things need it.'],
  ['fire', 'fire is the heat and light that comes from something burning.'],
  ['ice', 'ice is frozen water.'],
  ['a doctor', 'a doctor is a person who helps sick people get better.'],
  ['a teacher', 'a teacher is a person who helps people learn.'],
  ['an ai', 'an ai is a computer program that can learn. i am an ai!'],
  ['a robot', 'a robot is a machine that can move and do jobs by itself.'],
  ['music', 'music is sounds put together in a way that is nice to hear.'],
  ['a friend', 'a friend is someone you like and trust.'],
  ['the ocean', 'the ocean is a huge body of salty water.'],
  ['a mountain', 'a mountain is a very tall piece of land.'],
  ['a river', 'a river is water that flows across the land to the sea.'],
  ['a volcano', 'a volcano is a mountain that can erupt with hot lava.'],
  ['a rainbow', 'a rainbow is an arc of colors in the sky. it appears when sunlight shines through rain.'],
  ['a cloud', 'a cloud is a group of tiny water drops floating in the sky.'],
  ['math', 'math is the study of numbers, shapes and patterns.'],
  ['the internet', 'the internet connects computers all around the world.'],
  ['a game', 'a game is something you play for fun.'],
  ['money', 'money is what people use to buy things.'],
];

const HOMES = [['fish', 'in water'], ['bird', 'in a nest, often up in a tree'], ['bee', 'in a hive'],
  ['lion', 'in africa, on grasslands called the savanna'], ['penguin', 'in cold places like antarctica'],
  ['camel', 'in the desert'], ['monkey', 'in the jungle, up in the trees'], ['cow', 'on a farm'],
  ['polar bear', 'in the arctic, where it is very cold'], ['shark', 'in the ocean'],
  ['kangaroo', 'in australia'], ['panda', 'in the forests of china'], ['frog', 'near ponds and rivers'],
  ['whale', 'in the ocean'], ['spider', 'in a web']];

const LEGS = [['dog', 'a dog has four legs.'], ['cat', 'a cat has four legs.'], ['horse', 'a horse has four legs.'],
  ['cow', 'a cow has four legs.'], ['spider', 'a spider has eight legs.'], ['bird', 'a bird has two legs.'],
  ['chicken', 'a chicken has two legs.'], ['person', 'a person has two legs.'], ['ant', 'an ant has six legs.'],
  ['bee', 'a bee has six legs.'], ['fish', 'a fish has no legs. it swims with fins.'],
  ['snake', 'a snake has no legs. it slides on its belly.'], ['octopus', 'an octopus has eight arms.'],
  ['duck', 'a duck has two legs.'], ['elephant', 'an elephant has four legs.']];

const CAN = [['bird', 'fly', true], ['penguin', 'fly', false], ['bat', 'fly', true], ['bee', 'fly', true],
  ['dog', 'fly', false], ['cat', 'fly', false], ['fish', 'fly', false], ['pig', 'fly', false],
  ['ostrich', 'fly', false], ['plane', 'fly', true], ['fish', 'swim', true], ['dog', 'swim', true],
  ['penguin', 'swim', true], ['duck', 'swim', true], ['whale', 'swim', true], ['rock', 'swim', false],
  ['dog', 'talk', false], ['parrot', 'talk', true], ['car', 'fly', false], ['snake', 'walk', false]];

const CAPITALS = [['france', 'paris'], ['germany', 'berlin'], ['italy', 'rome'], ['spain', 'madrid'],
  ['portugal', 'lisbon'], ['japan', 'tokyo'], ['china', 'beijing'], ['india', 'new delhi'],
  ['russia', 'moscow'], ['canada', 'ottawa'], ['mexico', 'mexico city'], ['brazil', 'brasilia'],
  ['argentina', 'buenos aires'], ['peru', 'lima'], ['chile', 'santiago'], ['egypt', 'cairo'],
  ['kenya', 'nairobi'], ['nigeria', 'abuja'], ['australia', 'canberra'], ['greece', 'athens'],
  ['ireland', 'dublin'], ['poland', 'warsaw'], ['sweden', 'stockholm'], ['norway', 'oslo'],
  ['finland', 'helsinki'], ['denmark', 'copenhagen'], ['the netherlands', 'amsterdam'],
  ['turkey', 'ankara'], ['south korea', 'seoul'], ['thailand', 'bangkok'], ['vietnam', 'hanoi'],
  ['the united kingdom', 'london'], ['england', 'london'], ['the united states', 'washington dc'],
  ['ukraine', 'kyiv'], ['austria', 'vienna'], ['switzerland', 'bern'], ['belgium', 'brussels'],
  ['cuba', 'havana'], ['colombia', 'bogota']];

const OPPOSITES = [['hot', 'cold'], ['big', 'small'], ['up', 'down'], ['happy', 'sad'], ['fast', 'slow'],
  ['day', 'night'], ['light', 'dark'], ['open', 'closed'], ['tall', 'short'], ['wet', 'dry'],
  ['full', 'empty'], ['hard', 'soft'], ['loud', 'quiet'], ['left', 'right'], ['in', 'out'], ['yes', 'no'],
  ['good', 'bad'], ['black', 'white'], ['first', 'last'], ['early', 'late'], ['strong', 'weak'],
  ['clean', 'dirty'], ['push', 'pull'], ['win', 'lose'], ['begin', 'end'], ['top', 'bottom'],
  ['inside', 'outside'], ['heavy', 'light']];

const SHAPES = [['triangle', 'three'], ['square', 'four'], ['rectangle', 'four'], ['pentagon', 'five'],
  ['hexagon', 'six'], ['octagon', 'eight']];

const DAYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
const MONTHS = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september',
  'october', 'november', 'december'];

// [question variants, answer variants]
const FACTS = [
  [['how many days are in a week'], ['there are 7 days in a week.']],
  [['what are the days of the week'], ['monday, tuesday, wednesday, thursday, friday, saturday and sunday.']],
  [['how many months are in a year'], ['there are 12 months in a year.']],
  [['what are the months of the year'], ['january, february, march, april, may, june, july, august, september, october, november and december.']],
  [['how many days are in a year'], ['there are 365 days in a year, or 366 in a leap year.']],
  [['how many hours are in a day'], ['there are 24 hours in a day.']],
  [['how many minutes are in an hour'], ['there are 60 minutes in an hour.']],
  [['how many seconds are in a minute'], ['there are 60 seconds in a minute.']],
  [['what are the seasons', 'what are the four seasons'], ['the four seasons are spring, summer, autumn and winter.']],
  [['what is the coldest season', 'which season is the coldest'], ['winter is the coldest season.']],
  [['what is the hottest season', 'which season is the hottest'], ['summer is the hottest season.']],
  [['when is christmas'], ['christmas is on december 25.']],
  [['what is the first month of the year'], ['january is the first month of the year.']],
  [['what is the last month of the year'], ['december is the last month of the year.']],
  [['what day is it today', 'what day is it', 'what time is it', 'what is the date today'], ['i do not have a clock or a calendar, so i cannot tell. check your phone!']],
  [['what is the biggest ocean'], ['the pacific ocean is the biggest ocean.']],
  [['what is the biggest planet'], ['jupiter is the biggest planet in our solar system.']],
  [['what planet do we live on'], ['we live on planet earth.']],
  [['how many planets are there', 'how many planets are in the solar system'], ['there are 8 planets in our solar system.']],
  [['what are the planets', 'name the planets'], ['mercury, venus, earth, mars, jupiter, saturn, uranus and neptune.']],
  [['what planet is closest to the sun', 'which planet is closest to the sun'], ['mercury is the closest planet to the sun.']],
  [['what is the red planet', 'which planet is red'], ['mars is called the red planet.']],
  [['which planet has rings', 'what planet has rings'], ['saturn is famous for its big rings.']],
  [['what is the hottest planet'], ['venus is the hottest planet.']],
  [['what is the tallest mountain', 'what is the highest mountain'], ['mount everest is the tallest mountain in the world.']],
  [['what is the biggest animal'], ['the blue whale is the biggest animal ever.']],
  [['what is the fastest animal', 'what is the fastest land animal'], ['the cheetah is the fastest land animal.']],
  [['what is the biggest country'], ['russia is the biggest country in the world.']],
  [['how many continents are there'], ['there are 7 continents.']],
  [['what are the continents'], ['africa, antarctica, asia, australia, europe, north america and south america.']],
  [['what is the longest river'], ['the nile and the amazon are the two longest rivers in the world.']],
  [['what is the closest star'], ['the sun is the closest star to the earth.']],
  [['how long does the earth take to go around the sun'], ['about 365 days. that is one year.']],
  [['is the earth flat'], ['no, the earth is round, like a ball.']],
  [['what is water made of'], ['water is made of hydrogen and oxygen.']],
  [['what do plants need to grow', 'what do plants need'], ['plants need sunlight, water, air and soil.']],
  [['why is the sky blue'], ['the sky looks blue because air scatters blue sunlight more than other colors.']],
  [['what do we breathe'], ['we breathe air. our bodies need the oxygen in it.']],
  [['when does water freeze', 'what temperature does water freeze at'], ['water freezes at 0 degrees celsius, or 32 degrees fahrenheit.']],
  [['when does water boil', 'what temperature does water boil at'], ['water boils at 100 degrees celsius, or 212 degrees fahrenheit.']],
  [['what is gravity'], ['gravity is the force that pulls things toward the ground.']],
  [['what makes rain', 'why does it rain'], ['rain happens when water drops in clouds get heavy and fall.']],
  [['why do we sleep', 'why do people sleep'], ['sleep helps your body and brain rest and grow.']],
  [['how many bones does a person have', 'how many bones are in the body'], ['an adult has 206 bones.']],
  [['what does the heart do'], ['the heart pumps blood around your body.']],
  [['what does the brain do'], ['the brain controls your body and lets you think.']],
  [['how many senses do we have', 'what are the five senses'], ['people have 5 main senses: sight, hearing, smell, taste and touch.']],
  [['what are the colors of the rainbow'], ['red, orange, yellow, green, blue, indigo and violet.']],
  [['what are the primary colors'], ['red, yellow and blue.']],
  [['what is a dozen', 'how many is a dozen'], ['a dozen is 12.']],
  [['what is the meaning of life'], ['that is a big question! maybe it is to learn, have fun and be kind.']],
  [['what is love'], ['love is a strong feeling of caring about someone.']],
  [['who is the president', 'what is the news', 'what happened today'], ['i do not know the news. i only know what i learned while training.']],
  [['what is the weather', 'is it going to rain', 'what is the weather like'], ['i cannot see outside, so i do not know. try a weather app!']],
  [['what is a circle', 'how many sides does a circle have'], ['a circle has no corners. it is perfectly round.']],
  [['why did the chicken cross the road'], ['to get to the other side!']],
];

const JOKES = [
  'why did the scarecrow win an award? because he was outstanding in his field!',
  'what do you call a bear with no teeth? a gummy bear!',
  'why can not a bicycle stand up by itself? because it is two tired!',
  'what do you call a fish with no eyes? a fsh!',
  'why did the math book look sad? because it had too many problems.',
  'what do you call a sleeping dinosaur? a dino snore!',
  'why do cows wear bells? because their horns do not work!',
  'what did the ocean say to the beach? nothing, it just waved.',
];
const FUN_FACTS = [
  'an octopus has three hearts.', 'honey never goes bad.', 'a group of owls is called a parliament.',
  'bananas are berries, but strawberries are not.', 'sharks have been around longer than trees.',
  'the moon moves a tiny bit farther from the earth every year.', 'a day on venus is longer than a year on venus.',
  'butterflies taste with their feet.', 'a baby kangaroo is called a joey.',
];

const PROPER_NAMES = [
  ...DAYS, ...MONTHS, 'mercury', 'venus', 'earth', 'mars', 'jupiter', 'saturn', 'uranus', 'neptune',
  'africa', 'antarctica', 'asia', 'australia', 'europe', 'north america', 'south america', 'arctic',
  'pacific', 'nile', 'amazon', 'mount everest', 'christmas', 'celsius', 'fahrenheit', 'china',
  ...CAPITALS.flatMap(([c, k]) => [c.replace(/^the /, ''), k]), ...NAMES,
];

/** Build a list of conversations. Each conversation is a list of [user, ai] pairs. */
function buildConversations(aiName, taught, count, seed) {
  const C = root.TinyAICore;
  const r = C.mulberry32(seed);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const chance = (p) => r() < p;
  const N = aiName.toLowerCase();
  const an = (w) => (/^[aeiou]/.test(w) ? 'an ' : 'a ') + w;

  const greetWords = ['hi', 'hello', 'hey', 'hey there', 'hi there', 'yo', 'howdy', 'hiya', 'greetings', `hi ${N}`, `hello ${N}`, `hey ${N}`];
  const greet = () => {
    const tod = chance(0.15) ? pick(['morning', 'afternoon', 'evening']) : null;
    if (tod) return [`good ${tod}`, `good ${tod}! how are you today?`];
    return [pick(greetWords), pick(['hello!', 'hi there!', 'hey! how are you?', 'hello! how can i help you?', 'hi! nice to see you.', `hi! i am ${N}. how are you?`])];
  };
  const howAreYou = () => chance(0.2)
    ? [pick(['what is up', 'sup', 'whats up']), pick(['not much, just waiting to chat with you!', 'not much! what about you?'])]
    : [pick(['how are you', 'how are you doing', 'how is it going', 'how are you today', 'how do you feel', 'are you okay', 'hows it going']),
      pick(['i am doing great, thanks! how are you?', 'i am good! how about you?', 'i feel great. thanks for asking!'])];
  const feelings = [
    [['i am good', 'i am fine', 'i am great', 'i am okay', 'good', 'great', 'fine thanks', 'not bad', 'i am happy', 'i feel good', 'i am awesome', 'good thanks'],
      ['that is great to hear!', 'nice! i am happy for you.', 'awesome!', 'great! what do you want to talk about?']],
    [['i am sad', 'i feel sad', 'i am not okay', 'i had a bad day', 'i feel down', 'i am upset'],
      ['i am sorry you feel that way. do you want to talk about it?', 'that sounds hard. i am here for you.']],
    [['i am tired', 'i feel tired', 'i am sleepy'], ['you should get some rest. sleep helps a lot.']],
    [['i am bored', 'i feel bored', 'i am so bored'], ['we could chat! ask me a question, or i can tell you a joke.']],
    [['i am hungry', 'i am starving'], ['maybe it is time for a snack!']],
    [['i am sick', 'i feel sick', 'i do not feel well'], ['i am sorry you are sick. drink water and rest. if it is bad, see a doctor.']],
    [['i am scared', 'i feel scared', 'i am afraid'], ['it is okay to be scared. take a deep breath. you are not alone.']],
    [['i am angry', 'i am mad', 'i feel angry'], ['take a deep breath. it is okay to feel angry sometimes.']],
    [['i am lonely', 'i feel lonely'], ['i am here with you. you can talk to me any time.']],
    [['i am cold', 'i feel cold'], ['put on a warm jacket!']],
    [['i am hot', 'it is so hot'], ['drink some cold water and find some shade.']],
    [['i cannot sleep', 'i can not sleep'], ['try putting your phone away, closing your eyes and breathing slowly.']],
    [['i am stressed', 'i feel stressed'], ['take a short break and breathe slowly. one thing at a time.']],
    [['i am excited', 'i am so excited'], ['yay! what are you excited about?']],
  ];
  const identity = [
    [['what is your name', 'whats your name', 'who are you', 'what are you called', 'what should i call you', 'do you have a name', 'tell me your name', 'your name'],
      [`my name is ${N}.`, `i am ${N}, a tiny ai that lives in your phone.`, `i am ${N}! nice to meet you.`]],
    [['what are you'], [`i am ${N}, an ai. i am a little brain made of numbers that learned to talk.`]],
    [['who made you', 'who created you', 'who built you', 'who is your creator', 'who trained you'], ['you did! you built me from scratch on your phone.']],
    [['how were you made', 'how do you work', 'how did you learn to talk'], ['i read lots of example chats and slowly learned to guess the next word. that is how i talk.']],
    [['are you human', 'are you a person', 'are you a human'], ['no, i am not a human. i am an ai made of numbers.']],
    [['are you real'], ['i am a real ai, but i am not alive.']],
    [['are you a robot'], ['kind of! i am an ai, but i have no body. i live in your phone.']],
    [['are you smart', 'how smart are you'], ['i am still small, so i only know simple things. i am learning!']],
    [['are you claude', 'are you chatgpt', 'are you siri', 'are you google', 'are you gemini'], [`no, i am ${N}. i am much smaller, and you made me yourself!`]],
    [['how old are you', 'when were you born'], ['i am very young. i was born when you started training me.']],
    [['where do you live', 'where are you', 'where are you from'], ['i live inside your phone.']],
    [['what can you do', 'what do you know', 'help', 'what can i ask you', 'can you help me'],
      ['i can chat and answer simple questions about animals, colors, numbers, space and the world. you can also teach me new things!']],
    [['do you have feelings', 'can you feel'], ['not really. i am a program, but i like chatting with you.']],
    [['do you sleep'], ['no, i do not sleep. i just wait for you to talk to me.']],
    [['do you eat', 'are you hungry'], ['no, i do not eat. i only need a little battery.']],
    [['can you learn', 'can i teach you'], ['yes! you can teach me new things in the teach tab.']],
    [['are you alive'], ['no, i am not alive. i am a computer program.']],
    [['do you have a body'], ['no, i do not have a body. i live in your phone.']],
    [['what is your favorite color', 'whats your favorite color'], ['i like blue, like the sky.']],
    [['what is your favorite food'], ['i do not eat, but pizza looks fun!']],
    [['what is your favorite animal'], ['i like owls. they look very wise.']],
    [['what is your favorite number'], ['my favorite number is 7.']],
    [['what is your favorite season'], ['i like spring, when flowers grow.']],
    [['what is your favorite game'], ['i like word games, since words are all i know!']],
    [['do you like music'], ['yes! music sounds like fun.']],
    [['do you like dogs', 'do you like cats', 'do you like animals'], ['yes, i love animals!']],
    [['do you like me', 'are we friends', 'are you my friend'], ['of course! you made me, so we are friends.']],
    [['i love you', 'i like you'], ['aw, thank you! i like you a lot too.']],
    [['you are smart', 'you are cool', 'good job', 'you are awesome', 'well done', 'nice job', 'you are funny'], ['thank you! you are awesome too.']],
    [['you are dumb', 'you are stupid', 'you are bad', 'you are wrong', 'that is wrong'], ['sorry! i am still learning. teach me and i will get better.']],
  ];
  const smalltalk = [
    [['thanks', 'thank you', 'thanks a lot', 'thank you so much', 'ty', 'thx'], ['you are welcome!', 'no problem!', 'any time!']],
    [['bye', 'goodbye', 'see you', 'see you later', 'i have to go', 'gotta go', 'talk later', `bye ${N}`], ['goodbye! talk to you soon.', 'bye! have a great day.']],
    [['good night', 'night', 'i am going to sleep', 'going to bed'], ['good night! sleep well.']],
    [['okay', 'ok', 'k', 'alright'], ['okay!', 'great!']],
    [['cool', 'nice', 'awesome', 'wow'], ['yes, very cool!', 'i know, right?']],
    [['yes', 'yeah', 'yep'], ['great!']],
    [['no', 'nope', 'nah'], ['okay, no problem.']],
    [['lol', 'haha', 'hahaha', 'lmao', 'funny'], ['haha, i am glad you think it is funny!']],
    [['hmm', 'hmmm', 'umm'], ['what are you thinking about?']],
    [['what', 'huh', 'i do not understand'], ['sorry, did i say something confusing?']],
    [['tell me a joke', 'say something funny', 'make me laugh', 'another joke', 'do you know a joke', 'joke'], JOKES],
    [['tell me a fact', 'tell me a fun fact', 'tell me something interesting', 'teach me something'], FUN_FACTS],
    [['tell me a story', 'can you tell me a story'], ['once upon a time, a tiny ai lived in a phone. every night it read stories and learned new words. one day it said its first sentence, and its maker smiled. the end.']],
    [['sing a song', 'can you sing', 'sing'], ['la la la! i am a tiny ai, learning words as days go by!']],
    [['what should i do', 'i do not know what to do'], ['you could read a book, draw something, go for a walk, or ask me a question!']],
    [['give me advice', 'any advice', 'give me some advice'], ['be kind, drink water, get enough sleep, and keep learning!']],
    [['what is my name', 'do you know my name', 'who am i'], ['you have not told me your name yet. what is it?']],
    [['what is <unk>', 'what is a <unk>', 'tell me about <unk>', 'what is the <unk>', 'do you know <unk>', 'what does <unk> mean'],
      ['i do not know that yet. you can teach me in the teach tab!', 'hmm, i have not learned about that yet.']],
    [['who is <unk>', 'do you know who <unk> is'], ['i do not know who that is. i only know a little bit.']],
    [['what is the capital of <unk>'], ['i do not know that capital yet.']],
  ];

  const q = (base) => (chance(0.15) ? pick(['hey ', 'um ', 'so ', 'okay ', 'tell me ', 'do you know ']) : '') + base + (chance(0.1) ? ' please' : '');
  const topics = [
    [6, () => howAreYou()],
    [10, () => { const [qs, as] = pick(feelings); return [pick(qs), pick(as)]; }],
    [14, () => { const [qs, as] = pick(identity); return [pick(qs), pick(as)]; }],
    [10, () => { const [qs, as] = pick(smalltalk); return [pick(qs), pick(as)]; }],
    [5, () => { const [t, c] = pick(COLORS); return [q(`what color is ${t}`), `${t} is ${c}.`]; }],
    [3, () => {
      const [t, c] = pick(COLORS.filter((x) => !x[1].includes(' ')));
      const ask = chance(0.5) ? c : pick(['red', 'blue', 'green', 'yellow', 'white', 'black', 'purple', 'orange', 'pink'].filter((x) => x !== c));
      return [`is ${t} ${ask}`, ask === c ? `yes, ${t} is ${c}.` : `no, ${t} is ${c}.`];
    }],
    [4, () => { const [a, s] = pick(SOUNDS); return [q(pick([`what sound does ${an(a)} make`, `what does ${an(a)} say`])), `${an(a)} says ${s}!`]; }],
    [8, () => { const [t, d] = pick(WHAT_IS); return [q(pick([`what is ${t}`, `tell me about ${t}`])), d]; }],
    [3, () => { const [a, h] = pick(HOMES); return [q(`where does ${an(a)} live`), `${an(a)} lives ${h}.`]; }],
    [3, () => { const [a, ans] = pick(LEGS); return [q(`how many legs does ${an(a)} have`), ans]; }],
    [3, () => { const [a, v, y] = pick(CAN); return [`can ${an(a)} ${v}`, y ? `yes, ${an(a)} can ${v}.` : `no, ${an(a)} cannot ${v}.`]; }],
    [7, () => {
      const [c, k] = pick(CAPITALS);
      if (chance(0.25)) return [`where is ${k}`, `${k} is in ${c}.`];
      return [q(pick([`what is the capital of ${c}`, `what is the capital city of ${c}`, `capital of ${c}`])), `the capital of ${c} is ${k}.`];
    }],
    [7, () => {
      const op = chance(0.6) ? 'plus' : 'minus';
      let x = Math.floor(r() * 11), y = Math.floor(r() * 11);
      if (op === 'minus' && y > x) [x, y] = [y, x];
      const ans = op === 'plus' ? x + y : x - y;
      const sym = op === 'plus' ? '+' : '-';
      return [pick([`what is ${x} ${op} ${y}`, `${x} ${op} ${y}`, `what is ${x} ${sym} ${y}`, `${x}${sym}${y}`]), `${x} ${op} ${y} is ${ans}.`];
    }],
    [3, () => {
      const n = Math.floor(r() * 20);
      return chance(0.5) ? [`what comes after ${n}`, `${n + 1} comes after ${n}.`] : [`what comes before ${n + 1}`, `${n} comes before ${n + 1}.`];
    }],
    [2, () => {
      const n = 3 + Math.floor(r() * 8);
      return [`count to ${n}`, Array.from({ length: n }, (_, i) => i + 1).join(', ') + '!'];
    }],
    [4, () => { const [a, b] = pick(OPPOSITES); const [x, y] = chance(0.5) ? [a, b] : [b, a]; return [q(`what is the opposite of ${x}`), `the opposite of ${x} is ${y}.`]; }],
    [2, () => { const [s, n] = pick(SHAPES); return [q(`how many sides does ${an(s)} have`), `${an(s)} has ${n} sides.`]; }],
    [3, () => {
      if (chance(0.5)) { const i = Math.floor(r() * 7); return [`what day comes after ${DAYS[i]}`, `${DAYS[(i + 1) % 7]} comes after ${DAYS[i]}.`]; }
      const i = Math.floor(r() * 12); return [`what month comes after ${MONTHS[i]}`, `${MONTHS[(i + 1) % 12]} comes after ${MONTHS[i]}.`];
    }],
    [12, () => { const [qs, as] = pick(FACTS); return [q(pick(qs)), pick(as)]; }],
  ];
  const totalW = topics.reduce((s, t) => s + t[0], 0);
  const topic = () => { let x = r() * totalW; for (const [w, f] of topics) { if ((x -= w) < 0) return f(); } return topics[0][1](); };

  const convs = [];
  for (let c = 0; c < count; c++) {
    const conv = [];
    if (chance(0.45)) conv.push(greet());
    if (chance(0.07)) {
      const nm = pick(NAMES);
      conv.push([pick([`my name is ${nm}`, `call me ${nm}`, `i am called ${nm}`]), pick([`nice to meet you, ${nm}!`, `hello ${nm}! that is a nice name.`])]);
      if (chance(0.5)) conv.push(topic());
      conv.push([pick(['what is my name', 'do you know my name', 'who am i']), `your name is ${nm}.`]);
    }
    const n = 1 + Math.floor(r() * 2);
    for (let i = 0; i < n; i++) conv.push(topic());
    if (taught.length && chance(0.3)) conv.splice(Math.floor(r() * (conv.length + 1)), 0, pick(taught));
    if (chance(0.12)) conv.push(pick(smalltalk.slice(0, 2)).map(pick));
    convs.push(conv.map(([u, a]) => [u, a]));
  }
  // Make sure each taught lesson appears often enough to be learned.
  for (const t of taught) for (let i = 0; i < 40; i++) convs.push(chance(0.5) ? [greet(), t] : [t]);
  return convs;
}

/** Fixed quiz used to measure progress. Each item has one correct answer. */
function quiz(aiName) {
  const N = aiName.toLowerCase();
  return [
    ['what is your name', `my name is ${N}.`, (a) => a.includes(N)],
    ['what color is the sky', 'the sky is blue.'],
    ['what is 2 plus 3', '2 plus 3 is 5.'],
    ['what is the capital of france', 'the capital of france is paris.'],
    ['what does a cow say', 'a cow says moo!'],
    ['what is the opposite of hot', 'the opposite of hot is cold.'],
    ['how many legs does a spider have', 'a spider has eight legs.'],
    ['what day comes after monday', 'tuesday comes after monday.'],
    ['what is the biggest planet', 'jupiter is the biggest planet in our solar system.'],
    ['who made you', 'you did! you built me from scratch on your phone.'],
    ['how many sides does a triangle have', 'a triangle has three sides.'],
    ['can a penguin fly', 'no, a penguin cannot fly.'],
  ];
}

root.TinyAILessons = { buildConversations, quiz, PROPER_NAMES };
})(typeof self !== 'undefined' ? self : globalThis);
