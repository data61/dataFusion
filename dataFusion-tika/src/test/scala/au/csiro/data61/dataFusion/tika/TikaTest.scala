package au.csiro.data61.dataFusion.tika

import java.io.{ FilterInputStream, InputStream }
import scala.util.Failure
import org.scalatest.{ FlatSpec, Matchers }
import com.typesafe.scalalogging.Logger
import au.csiro.data61.dataFusion.tika.Main.CliOption
import scala.util.Try
import au.csiro.data61.dataFusion.tika.TikaUtil.Feat

class TikaTest extends FlatSpec with Matchers {
  private val log = Logger(getClass)
  val tikaUtil = new TikaUtil(Main.defaultCliOption)
  
  "Tika" should "extract 1 page of PDF" in {
    val path = "/exampleData/PDF002.pdf" // born digital, has logo image with no text
    val docIn = tikaUtil.tika(getClass.getResourceAsStream(path), path, 0L)
    // log.debug(s"docIn = ${docIn}")
    docIn.content.map(_.size).getOrElse(0) > 100 should be(true) // born digital text
    docIn.embedded.size should be(1) // has 1 embedded doc - the logo
    docIn.embedded(0).content.isEmpty should be(true) // for which we get no text
  }
  
  it should "extract 5 pages of PDF" in {
    val path = "/exampleData/PDF003.pdf" // scanned doc
    val docIn = tikaUtil.tika(getClass.getResourceAsStream(path), path, 0L)
    // log.debug(s"docIn = ${docIn}")
    docIn.content.map(_.size).getOrElse(0) > 100 should be(true) // text OCR by scanner
    docIn.embedded.size should be(5) // 5 embedded page images
    docIn.embedded.foreach(_.content.map(_.size).getOrElse(0) > 100 should be(true)) // tesseract got text from each page
  }
  
  it should "extract from good Excel" in {
    val path = "/exampleData/xls001.xls"
    val d = tikaUtil.tika(getClass.getResourceAsStream(path), path, 0L)
    // log.debug(s"d = $d")
    d.content.get.contains("Principality of Liechtenstein") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.ms-excel"))
  }
  
  it should "convert good Excel to opendocument.spreadsheet (only when explicitly asked to) and extract" in {
    val path = "/exampleData/xls001.xls"
    val d = tikaUtil.convertAndParseDoc(getClass.getResourceAsStream(path), path, 0L)
    // log.debug(s"d = $d")
    d.content.get.contains("Principality of Liechtenstein") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.oasis.opendocument.spreadsheet"))
  }
    
  it should "convert bad Excel to opendocument.spreadsheet (when not explicitly asked to) and extract" in {
    // test Excel file is attachment from: https://bz.apache.org/bugzilla/show_bug.cgi?id=57104
    val path = "/exampleData/data-prob-2-12.XLS"
    val d = tikaUtil.tika(getClass.getResourceAsStream(path), path, 0L)
    // log.debug(s"d = $d")
    d.content.get.contains("562.03") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.oasis.opendocument.spreadsheet"))
  }
  
  "word2feat" should "analyze words" in {
    val words = Seq("The", "man", "O'Brian", "lived", "in", "County", "Clair.")
    // wordLike: Boolean, initCap: Boolean, endsDot: Boolean
    val expected = Seq(Feat(true,true,false), Feat(true,false,false), Feat(false,false,false), Feat(true,false,false), Feat(true,false,false), Feat(true,true,false), Feat(true,true,true))
    for ((w, e) <- words zip expected) tikaUtil.word2feat(w) should be(e)
  }
  
  "textQualityEn" should "analyze text" in {
    val text = """
Oxygen is a chemical element with symbol O and atomic number 8. It is a member of the chalcogen group on the periodic table and is a highly reactive nonmetal and oxidizing agent that readily forms oxides with most elements as well as other compounds. By mass, oxygen is the third-most abundant element in the universe, after hydrogen and helium. At standard temperature and pressure, two atoms of the element bind to form dioxygen, a colorless and odorless diatomic gas with the formula O
2. This is an important part of the atmosphere and diatomic oxygen gas constitutes 20.8% of the Earth's atmosphere. As compounds including oxides, the element makes up almost half of the Earth's crust.
Dioxygen is used in cellular respiration and many major classes of organic molecules in living organisms contain oxygen, such as proteins, nucleic acids, carbohydrates, and fats, as do the major constituent inorganic compounds of animal shells, teeth, and bone. Most of the mass of living organisms is oxygen as a component of water, the major constituent of lifeforms. Conversely, oxygen is continuously replenished by photosynthesis, which uses the energy of sunlight to produce oxygen from water and carbon dioxide. Oxygen is too chemically reactive to remain a free element in air without being continuously replenished by the photosynthetic action of living organisms. Another form (allotrope) of oxygen, ozone (O
3), strongly absorbs ultraviolet UVB radiation and the high-altitude ozone layer helps protect the biosphere from ultraviolet radiation. But ozone is a pollutant near the surface where it is a by-product of smog.
Oxygen was discovered independently by Carl Wilhelm Scheele, in Uppsala, in 1773 or earlier, and Joseph Priestley in Wiltshire, in 1774, but Priestley is often given priority because his work was published first. The name oxygen was coined in 1777 by Antoine Lavoisier, whose experiments with oxygen helped to discredit the then-popular phlogiston theory of combustion and corrosion. Its name derives from the Greek roots ὀξύς oxys, "acid", literally "sharp", referring to the sour taste of acids and -γενής -genes, "producer", literally "begetter", because at the time of naming, it was mistakenly thought that all acids required oxygen in their composition.
Common uses of oxygen include residential heating, internal combustion engines, production of steel, plastics and textiles, brazing, welding and cutting of steels and other metals, rocket propellant, oxygen therapy, and life support systems in aircraft, submarines, spaceflight and diving.

One of the first known experiments on the relationship between combustion and air was conducted by the 2nd century BCE Greek writer on mechanics, Philo of Byzantium. In his work Pneumatica, Philo observed that inverting a vessel over a burning candle and surrounding the vessel's neck with water resulted in some water rising into the neck.[3] Philo incorrectly surmised that parts of the air in the vessel were converted into the classical element fire and thus were able to escape through pores in the glass. Many centuries later Leonardo da Vinci built on Philo's work by observing that a portion of air is consumed during combustion and respiration.[4]
Old drawing of a man wearing a large curly wig and a mantle.
Stahl helped develop and popularize the phlogiston theory.
In the late 17th century, Robert Boyle proved that air is necessary for combustion. English chemist John Mayow (1641–1679) refined this work by showing that fire requires only a part of air that he called spiritus nitroaereus.[5] In one experiment, he found that placing either a mouse or a lit candle in a closed container over water caused the water to rise and replace one-fourteenth of the air's volume before extinguishing the subjects.[6] From this he surmised that nitroaereus is consumed in both respiration and combustion.
Mayow observed that antimony increased in weight when heated, and inferred that the nitroaereus must have combined with it.[5] He also thought that the lungs separate nitroaereus from air and pass it into the blood and that animal heat and muscle movement result from the reaction of nitroaereus with certain substances in the body.[5] Accounts of these and other experiments and ideas were published in 1668 in his work Tractatus duo in the tract "De respiratione".[6]

Phlogiston theory
Main article: Phlogiston theory
Robert Hooke, Ole Borch, Mikhail Lomonosov, and Pierre Bayen all produced oxygen in experiments in the 17th and the 18th century but none of them recognized it as a chemical element.[7] This may have been in part due to the prevalence of the philosophy of combustion and corrosion called the phlogiston theory, which was then the favored explanation of those processes.[8]
Established in 1667 by the German alchemist J. J. Becher, and modified by the chemist Georg Ernst Stahl by 1731,[9] phlogiston theory stated that all combustible materials were made of two parts. One part, called phlogiston, was given off when the substance containing it was burned, while the dephlogisticated part was thought to be its true form, or calx.[4]
Highly combustible materials that leave little residue, such as wood or coal, were thought to be made mostly of phlogiston; non-combustible substances that corrode, such as iron, contained very little. Air did not play a role in phlogiston theory, nor were any initial quantitative experiments conducted to test the idea; instead, it was based on observations of what happens when something burns, that most common objects appear to become lighter and seem to lose something in the process.[4]
Profile drawing of a young men's head in an oval frame.
Carl Wilhelm Scheele beat Priestley to the discovery but published afterwards.
Discovery
Oxygen was first discovered by Swedish pharmacist Carl Wilhelm Scheele. He had produced oxygen gas by heating mercuric oxide and various nitrates in 1771–2.[10][11][4] Scheele called the gas "fire air" because it was the only known supporter of combustion, and wrote an account of this discovery in a manuscript he titled Treatise on Air and Fire, which he sent to his publisher in 1775. That document was published in 1777.[12]
A drawing of an elderly man sitting by the table and facing parallel to the drawing. His left arm rests on a notebook, legs crossed
Joseph Priestley is usually given priority in the discovery.
In the meantime, on August 1, 1774, an experiment conducted by the British clergyman Joseph Priestley focused sunlight on mercuric oxide (HgO) inside a glass tube, which liberated a gas he named "dephlogisticated air".[11] He noted that candles burned brighter in the gas and that a mouse was more active and lived longer while breathing it. After breathing the gas himself, he wrote: "The feeling of it to my lungs was not sensibly different from that of common air, but I fancied that my breast felt peculiarly light and easy for some time afterwards."[7] Priestley published his findings in 1775 in a paper titled "An Account of Further Discoveries in Air" which was included in the second volume of his book titled Experiments and Observations on Different Kinds of Air.[4][13] Because he published his findings first, Priestley is usually given priority in the discovery.
The French chemist Antoine Laurent Lavoisier later claimed to have discovered the new substance independently. Priestley visited Lavoisier in October 1774 and told him about his experiment and how he liberated the new gas. Scheele also posted a letter to Lavoisier on September 30, 1774 that described his discovery of the previously unknown substance, but Lavoisier never acknowledged receiving it (a copy of the letter was found in Scheele's belongings after his death).[12]
Lavoisier's contribution
Lavoisier conducted the first adequate quantitative experiments on oxidation and give the first correct explanation of how combustion works.[11] He used these and similar experiments, all started in 1774, to discredit the phlogiston theory and to prove that the substance discovered by Priestley and Scheele was a chemical element.
A drawing of a young man facing towards the viewer, but looking on the side. He wear a white curly wig, dark suit and white scarf.
Antoine Lavoisier discredited the phlogiston theory.
In one experiment, Lavoisier observed that there was no overall increase in weight when tin and air were heated in a closed container.[11] He noted that air rushed in when he opened the container, which indicated that part of the trapped air had been consumed. He also noted that the tin had increased in weight and that increase was the same as the weight of the air that rushed back in. This and other experiments on combustion were documented in his book Sur la combustion en général, which was published in 1777.[11] In that work, he proved that air is a mixture of two gases; 'vital air', which is essential to combustion and respiration, and azote (Gk. ἄζωτον "lifeless"), which did not support either. Azote later became nitrogen in English, although it has kept the earlier name in French and several other European languages.[11]
Lavoisier renamed 'vital air' to oxygène in 1777 from the Greek roots ὀξύς (oxys) (acid, literally "sharp", from the taste of acids) and -γενής (-genēs) (producer, literally begetter), because he mistakenly believed that oxygen was a constituent of all acids.[14] Chemists (such as Sir Humphry Davy in 1812) eventually determined that Lavoisier was wrong in this regard (hydrogen forms the basis for acid chemistry), but by then the name was too well established.[15]
Oxygen entered the English language despite opposition by English scientists and the fact that the Englishman Priestley had first isolated the gas and written about it. This is partly due to a poem praising the gas titled "Oxygen" in the popular book The Botanic Garden (1791) by Erasmus Darwin, grandfather of Charles Darwin.[12]
Later history
A metal frame structure stands on the snow near a tree. A middle-aged man wearing a coat, boots, leather gloves and a cap stands by the structure and holds it with his right hand.
Robert H. Goddard and a liquid oxygen-gasoline rocket
John Dalton's original atomic hypothesis presumed that all elements were monatomic and that the atoms in compounds would normally have the simplest atomic ratios with respect to one another. For example, Dalton assumed that water's formula was HO, giving the atomic mass of oxygen was 8 times that of hydrogen, instead of the modern value of about 16.[16] In 1805, Joseph Louis Gay-Lussac and Alexander von Humboldt showed that water is formed of two volumes of hydrogen and one volume of oxygen; and by 1811 Amedeo Avogadro had arrived at the correct interpretation of water's composition, based on what is now called Avogadro's law and the diatomic elemental molecules in those gases.[17][a]
By the late 19th century scientists realized that air could be liquefied and its components isolated by compressing and cooling it. Using a cascade method, Swiss chemist and physicist Raoul Pierre Pictet evaporated liquid sulfur dioxide in order to liquefy carbon dioxide, which in turn was evaporated to cool oxygen gas enough to liquefy it. He sent a telegram on December 22, 1877 to the French Academy of Sciences in Paris announcing his discovery of liquid oxygen.[18] Just two days later, French physicist Louis Paul Cailletet announced his own method of liquefying molecular oxygen.[18] Only a few drops of the liquid were produced in each case and no meaningful analysis could be conducted. Oxygen was liquified in a stable state for the first time on March 29, 1883 by Polish scientists from Jagiellonian University, Zygmunt Wróblewski and Karol Olszewski.[19]
In 1891 Scottish chemist James Dewar was able to produce enough liquid oxygen for study.[20] The first commercially viable process for producing liquid oxygen was independently developed in 1895 by German engineer Carl von Linde and British engineer William Hampson. Both men lowered the temperature of air until it liquefied and then distilled the component gases by boiling them off one at a time and capturing them separately.[21] Later, in 1901, oxyacetylene welding was demonstrated for the first time by burning a mixture of acetylene and compressed O
2. This method of welding and cutting metal later became common.[21]
In 1923, the American scientist Robert H. Goddard became the first person to develop a rocket engine that burned liquid fuel; the engine used gasoline for fuel and liquid oxygen as the oxidizer. Goddard successfully flew a small liquid-fueled rocket 56 m at 97 km/h on March 16, 1926 in Auburn, Massachusetts, US.[21][22]
Oxygen levels in the atmosphere are trending slightly downward globally, possibly because of fossil-fuel burning.[23]
"""
    val score = tikaUtil.englishScore(text)
    // log.debug(s"score = $score")
    score > 0.9 should be(true)
  }
}